/*
 * Partition indexing and height carving follow TerraFirmaCraft/TerraFirmaGreg
 * river architecture. Those projects are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.river;

import gregtech.worldgen.earth.region.EarthRegion;
import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import gregtech.worldgen.earth.region.EarthRegionGenerator;
import gregtech.worldgen.earth.region.EarthRiverEdge;
import gregtech.worldgen.earth.region.EarthUnits;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Block-scale lookup and terrain influence for regional river edges. */
public final class EarthRiverSampler {
    private static final int MAX_CACHED_PARTITION_CELLS = 256;
    private static final double MAX_SAMPLE_DISTANCE_BLOCKS = 50.0;
    private static final double MAX_SAMPLE_DISTANCE_GRID =
            MAX_SAMPLE_DISTANCE_BLOCKS / EarthUnits.GRID_WIDTH_IN_BLOCK;
    private static final double MAX_SAMPLE_DISTANCE_BLOCKS_SQ =
            MAX_SAMPLE_DISTANCE_BLOCKS * MAX_SAMPLE_DISTANCE_BLOCKS;

    private final EarthRegionGenerator regions;
    private final FractalNoise floodplainDistance;
    private final FractalNoise wideBase;
    private final FractalNoise wideDistance;
    private final FractalNoise canyonBase;
    private final FractalNoise canyonDistance;
    private final FractalNoise canyonShape;
    private final ConcurrentHashMap<Long, PartitionCell> partitionCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Long> cacheOrder = new ConcurrentLinkedDeque<>();

    public EarthRiverSampler(EarthRegionGenerator regions, long seed) {
        this.regions = regions;
        XoroshiroRandomSource sequence = new XoroshiroRandomSource(seed);
        floodplainDistance = new FractalNoise(sequence.nextLong(), 4, 0.05);
        wideBase = new FractalNoise(sequence.nextLong(), 4, 0.05);
        wideDistance = new FractalNoise(sequence.nextLong(), 4, 0.05);
        canyonBase = new FractalNoise(sequence.nextLong(), 4, 0.05);
        canyonDistance = new FractalNoise(sequence.nextLong(), 4, 0.05);
        canyonShape = new FractalNoise(sequence.nextLong(), 1, 0.0007);
    }

    public @Nullable RiverInfo sample(int blockX, int blockZ) {
        PartitionPoint point = partitionPoint(blockX, blockZ);
        double exactGridX = EarthUnits.blockToGridExact(blockX);
        double exactGridZ = EarthUnits.blockToGridExact(blockZ);
        double bestNormalizedDistance = Double.MAX_VALUE;
        double bestDistanceBlocksSq = Double.MAX_VALUE;
        double bestWidthSq = 0.0;
        EarthRiverEdge bestEdge = null;

        for (EarthRiverEdge edge : point.rivers()) {
            if (!edge.fractal().maybeIntersect(
                    exactGridX, exactGridZ, MAX_SAMPLE_DISTANCE_GRID)) {
                continue;
            }
            double distanceGridSq = edge.fractal().intersectDistance(exactGridX, exactGridZ);
            double distanceBlocksSq = distanceGridSq
                    * EarthUnits.GRID_WIDTH_IN_BLOCK * EarthUnits.GRID_WIDTH_IN_BLOCK;
            if (distanceBlocksSq >= MAX_SAMPLE_DISTANCE_BLOCKS_SQ) continue;

            double widthSq = edge.widthSq(exactGridX, exactGridZ);
            double normalized = distanceBlocksSq / widthSq;
            if (normalized < bestNormalizedDistance) {
                bestNormalizedDistance = normalized;
                bestDistanceBlocksSq = distanceBlocksSq;
                bestWidthSq = widthSq;
                bestEdge = edge;
            }
        }
        return bestEdge == null
                ? null
                : new RiverInfo(bestEdge, bestDistanceBlocksSq, bestWidthSq);
    }

    /**
     * TFG-style weighted river height modifier. Climate-specific cave rivers
     * are intentionally mapped to canyons until the 3D density milestone.
     */
    public double adjustHeight(
            double height,
            int blockX,
            int blockZ,
            @Nullable RiverInfo info,
            double[] profileWeights
    ) {
        if (info == null) return height;
        double[] blendWeights = new double[BlendType.values().length];
        TerrainProfile[] profiles = TerrainProfile.values();
        for (int index = 0; index < profiles.length; index++) {
            blendWeights[blendType(profiles[index]).ordinal()] += profileWeights[index];
        }

        double output = 0.0;
        for (BlendType type : BlendType.values()) {
            double weight = blendWeights[type.ordinal()];
            if (weight <= 0.0) continue;
            double candidate = switch (type) {
                case NONE -> height;
                case FLOODPLAIN -> floodplainHeight(info, blockX, blockZ, height);
                case WIDE -> wideHeight(info, blockX, blockZ, height);
                case CANYON -> canyonHeight(info, blockX, blockZ, height);
            };
            output += weight * candidate;
        }
        return output;
    }

    public int cachedPartitionCellCount() {
        return partitionCache.size();
    }

    private PartitionPoint partitionPoint(int blockX, int blockZ) {
        int gridX = EarthUnits.blockToGrid(blockX);
        int gridZ = EarthUnits.blockToGrid(blockZ);
        int partitionX = EarthUnits.gridToPartition(gridX);
        int partitionZ = EarthUnits.gridToPartition(gridZ);
        int cellX = EarthUnits.partitionToCell(partitionX);
        int cellZ = EarthUnits.partitionToCell(partitionZ);
        PartitionCell cell = getOrCreatePartitionCell(cellX, cellZ);
        return cell.get(partitionX, partitionZ);
    }

    private PartitionCell getOrCreatePartitionCell(int cellX, int cellZ) {
        long key = ChunkPos.asLong(cellX, cellZ);
        PartitionCell cell = partitionCache.computeIfAbsent(key, ignored -> {
            PartitionCell generated = createPartitionCell(cellX, cellZ);
            cacheOrder.addLast(key);
            return generated;
        });
        trimCache();
        return cell;
    }

    private PartitionCell createPartitionCell(int cellX, int cellZ) {
        PartitionCell partition = new PartitionCell(cellX, cellZ);
        for (int ownerX = cellX - 1; ownerX <= cellX + 1; ownerX++) {
            for (int ownerZ = cellZ - 1; ownerZ <= cellZ + 1; ownerZ++) {
                EarthRegion region = regions.getOrCreateRegionForOwner(ownerX, ownerZ);
                for (EarthRiverEdge edge : region.rivers()) {
                    int minX = Math.max(edge.minPartX(), partition.minPartX);
                    int minZ = Math.max(edge.minPartZ(), partition.minPartZ);
                    int maxX = Math.min(edge.maxPartX(), partition.maxPartX);
                    int maxZ = Math.min(edge.maxPartZ(), partition.maxPartZ);
                    for (int partX = minX; partX <= maxX; partX++) {
                        for (int partZ = minZ; partZ <= maxZ; partZ++) {
                            partition.get(partX, partZ).rivers.add(edge);
                        }
                    }
                }
            }
        }
        partition.freeze();
        return partition;
    }

    private void trimCache() {
        while (partitionCache.size() > MAX_CACHED_PARTITION_CELLS) {
            Long oldest = cacheOrder.pollFirst();
            if (oldest == null) return;
            partitionCache.remove(oldest);
        }
    }

    private double floodplainHeight(RiverInfo info, int x, int z, double height) {
        double distance = info.normalizedDistanceSq() * 0.8
                + floodplainDistance.sample(x, z) * 0.2;
        double riverHeight;
        if (distance < 1.0) {
            riverHeight = 58.5 + distance * 3.0;
        } else if (distance < 2.0) {
            riverHeight = 61.5;
        } else {
            double terrainWeight = Math.clamp(2.0 * distance - 4.0, 0.0, 1.0);
            riverHeight = 61.5 * (1.0 - terrainWeight) + height * terrainWeight;
        }
        return Math.min(riverHeight, height);
    }

    private double wideHeight(RiverInfo info, int x, int z, double height) {
        double distance = info.normalizedDistanceSq() * 0.8
                + wideDistance.sample(x, z) * 0.15;
        double riverHeight = 58.0 + distance * 7.0
                + map(wideBase.sample(x, z), -1.0, 1.0, -2.5, 1.5);
        return Math.min(riverHeight, height);
    }

    private double canyonHeight(RiverInfo info, int x, int z, double height) {
        double distance = info.normalizedDistanceSq() * 1.3
                + map(canyonDistance.sample(x, z), -1.0, 1.0, -0.3, 0.2);
        double adjusted = distance > 0.6 ? distance * 0.4 + 0.8 : distance;
        double shape = Math.clamp((canyonShape.sample(x, z) + 1.0) * 0.5, 0.0, 1.0);
        double riverHeight = 55.0 + lerp(shape, distance, adjusted) * 16.0
                + map(canyonBase.sample(x, z), -1.0, 1.0, -7.0, 3.0);
        return Math.min(riverHeight, height);
    }

    private static BlendType blendType(TerrainProfile profile) {
        return switch (profile) {
            case DEEP_OCEAN, OCEAN, LAKE -> BlendType.NONE;
            case PLAINS, HILLS -> BlendType.FLOODPLAIN;
            case SHORE, TIDAL_FLATS, COASTAL_DUNES -> BlendType.WIDE;
            case ISLAND, ROLLING_HILLS, HIGHLANDS, MOUNTAINS, COASTAL_MOUNTAINS, VOLCANO,
                    EMBAYMENTS, ROCKY_SHORES -> BlendType.CANYON;
        };
    }

    private static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private enum BlendType {
        NONE,
        FLOODPLAIN,
        WIDE,
        CANYON
    }

    public record RiverInfo(EarthRiverEdge edge, double distanceSq, double widthSq) {
        public double normalizedDistanceSq() {
            return distanceSq / widthSq;
        }
    }

    private static final class PartitionCell {
        private final int minPartX;
        private final int minPartZ;
        private final int maxPartX;
        private final int maxPartZ;
        private final PartitionPoint[] data = new PartitionPoint[
                EarthUnits.CELL_WIDTH_IN_PARTITION * EarthUnits.CELL_WIDTH_IN_PARTITION
                ];

        private PartitionCell(int cellX, int cellZ) {
            minPartX = EarthUnits.cellToPartition(cellX);
            minPartZ = EarthUnits.cellToPartition(cellZ);
            maxPartX = minPartX + EarthUnits.CELL_WIDTH_IN_PARTITION - 1;
            maxPartZ = minPartZ + EarthUnits.CELL_WIDTH_IN_PARTITION - 1;
            for (int index = 0; index < data.length; index++) {
                data[index] = new PartitionPoint(new ArrayList<>());
            }
        }

        private PartitionPoint get(int partitionX, int partitionZ) {
            int localX = partitionX - minPartX;
            int localZ = partitionZ - minPartZ;
            if (localX < 0 || localZ < 0
                    || localX >= EarthUnits.CELL_WIDTH_IN_PARTITION
                    || localZ >= EarthUnits.CELL_WIDTH_IN_PARTITION) {
                throw new IndexOutOfBoundsException("River partition outside cached cell");
            }
            return data[localX | localZ << EarthUnits.PARTITION_BITS];
        }

        private void freeze() {
            for (PartitionPoint point : data) {
                point.rivers = List.copyOf(point.rivers);
            }
        }
    }

    private static final class PartitionPoint {
        private List<EarthRiverEdge> rivers;

        private PartitionPoint(List<EarthRiverEdge> rivers) {
            this.rivers = rivers;
        }

        private List<EarthRiverEdge> rivers() {
            return rivers;
        }
    }

    private static final class FractalNoise {
        private final SimplexNoise[] octaves;
        private final double initialFrequency;
        private final double amplitudeSum;

        private FractalNoise(long seed, int octaveCount, double spread) {
            XoroshiroRandomSource random = new XoroshiroRandomSource(seed);
            octaves = new SimplexNoise[octaveCount];
            double amplitude = 1.0;
            double sum = 0.0;
            for (int index = 0; index < octaveCount; index++) {
                octaves[index] = new SimplexNoise(random);
                sum += amplitude;
                amplitude *= 0.5;
            }
            initialFrequency = spread / (1 << Math.max(0, octaveCount - 1));
            amplitudeSum = sum;
        }

        private double sample(double x, double z) {
            double frequency = initialFrequency;
            double amplitude = 1.0;
            double value = 0.0;
            for (SimplexNoise octave : octaves) {
                value += octave.getValue(x * frequency, z * frequency) * amplitude;
                frequency *= 2.0;
                amplitude *= 0.5;
            }
            return Math.clamp(value / amplitudeSum, -1.0, 1.0);
        }
    }
}
