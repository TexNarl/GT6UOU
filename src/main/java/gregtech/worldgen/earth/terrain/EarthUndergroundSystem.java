/*
 * The cave-density -> aquifer -> host-rock split follows TerraFirmaCraft's
 * ChunkNoiseFiller and TFCAquifer architecture. TerraFirmaCraft and
 * TerraFirmaGreg are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.terrain;

import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.region.EarthRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seed-wired underground sampler shared by every Earth chunk.
 *
 * <p>The expensive cave fields are sampled on the same 4 x 8 block lattice
 * used by TFC and trilinearly interpolated inside a chunk. Aquifers are a
 * separate decision made only after cave density has produced a void.</p>
 */
public final class EarthUndergroundSystem {
    private static final int CELL_WIDTH = 4;
    private static final int CELL_HEIGHT = 8;
    private static final int SURFACE_INTEGRITY_DEPTH = 6;
    private static final int MAX_CACHED_AQUIFERS = 256;

    private final EarthGeneratorState state;
    private final CaveNoise caveNoise;
    private final PositionalRandomFactory aquiferRandom;
    private final NormalNoise barrierNoise;
    private final long fluidCellSeed;
    private final ConcurrentHashMap<Long, EarthAquifer> aquiferCache = new ConcurrentHashMap<>();

    public EarthUndergroundSystem(EarthGeneratorState state, RandomState randomState) {
        this.state = state;
        this.caveNoise = new CaveNoise(randomState);
        this.aquiferRandom = randomState.getOrCreateRandomFactory(
                ResourceLocation.fromNamespaceAndPath("gt6uou", "earth_aquifer")
        );
        this.barrierNoise = randomState.getOrCreateNoise(Noises.AQUIFER_BARRIER);
        this.fluidCellSeed = aquiferRandom.fromHashOf("earth_aquifer_fluid_cells").nextLong();
    }

    public ChunkSampler prepare(ChunkAccess chunk, EarthNoiseFiller.ChunkTerrain terrain) {
        return new ChunkSampler(chunk, terrain);
    }

    /**
     * Returns the same deterministic aquifer instance to the density filler and
     * the later cave/canyon carver pass, matching TFC's bounded per-chunk
     * aquifer cache.
     */
    public Aquifer aquifer(ChunkAccess chunk) {
        long key = chunk.getPos().toLong();
        EarthAquifer aquifer = aquiferCache.computeIfAbsent(key, ignored -> new EarthAquifer(chunk));
        trimAquiferCache();
        return aquifer;
    }

    private EarthAquifer earthAquifer(ChunkAccess chunk) {
        return (EarthAquifer) aquifer(chunk);
    }

    private void trimAquiferCache() {
        while (aquiferCache.size() > MAX_CACHED_AQUIFERS) {
            var iterator = aquiferCache.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            aquiferCache.remove(iterator.next());
        }
    }

    public final class ChunkSampler {
        private final CaveLattice caves;
        private final EarthAquifer aquifer;
        private final EarthNoiseFiller.ChunkTerrain terrain;

        private ChunkSampler(ChunkAccess chunk, EarthNoiseFiller.ChunkTerrain terrain) {
            this.caves = new CaveLattice(chunk, terrain);
            this.aquifer = earthAquifer(chunk);
            this.terrain = terrain;
        }

        /** Positive is solid; zero or negative is a cave void. */
        public double density(int localX, int y, int localZ, int surfaceY) {
            int depth = surfaceY - y;
            double terrainDensity = Mth.clamp((depth + 1) * 0.2D, -1.0D, 1.0D);
            if (depth < SURFACE_INTEGRITY_DEPTH) {
                return terrainDensity;
            }
            double caveDensity = caves.sample(localX, y, localZ);
            if (terrain.river(localX, localZ)
                    && (terrain.karst(localX, localZ)
                    || terrain.profile(localX, localZ) == EarthRegion.TerrainProfile.HIGHLANDS
                    || terrain.profile(localX, localZ) == EarthRegion.TerrainProfile.MOUNTAINS
                    || terrain.profile(localX, localZ) == EarthRegion.TerrainProfile.COASTAL_MOUNTAINS)) {
                // TFG cave-river semantics: a narrow aquifer-aware tunnel
                // follows the same continuous surface river path in terrain
                // families whose river blend is underground/canyon-like.
                int centerY = surfaceY - 18;
                double tunnel = Math.abs(y - centerY) / 3.5 - 1.0;
                caveDensity = Math.min(caveDensity, tunnel);
            }
            return Math.min(terrainDensity, caveDensity);
        }

        /** Returns null for solid rock, otherwise cave air/water/lava. */
        public BlockState substance(int x, int y, int z, double density) {
            return aquifer.sampleState(x, y, z, density);
        }

        public boolean shouldScheduleFluidUpdate() {
            return aquifer.shouldScheduleFluidUpdate;
        }
    }

    /**
     * Chunk-local 4 x 8 lattice. TFC interpolates its registered density
     * functions on this lattice; keeping the lattice avoids evaluating the
     * full cave graph for every underground block.
     */
    private final class CaveLattice {
        private static final int GRID_XZ = 5;

        private final int gridMinY;
        private final int gridY;
        private final double[] caves;
        private final double[] noodleToggle;
        private final double[] noodleThickness;
        private final double[] noodleRidgeA;
        private final double[] noodleRidgeB;

        private CaveLattice(ChunkAccess chunk, EarthNoiseFiller.ChunkTerrain terrain) {
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            this.gridMinY = Math.floorDiv(terrain.minY(), CELL_HEIGHT) * CELL_HEIGHT;
            int maxY = terrain.maxSurfaceY();
            this.gridY = Math.floorDiv(maxY - gridMinY + CELL_HEIGHT - 1, CELL_HEIGHT) + 1;
            int size = GRID_XZ * GRID_XZ * gridY;
            this.caves = new double[size];
            this.noodleToggle = new double[size];
            this.noodleThickness = new double[size];
            this.noodleRidgeA = new double[size];
            this.noodleRidgeB = new double[size];

            for (int gx = 0; gx < GRID_XZ; gx++) {
                int x = minX + gx * CELL_WIDTH;
                for (int gz = 0; gz < GRID_XZ; gz++) {
                    int z = minZ + gz * CELL_WIDTH;
                    for (int gy = 0; gy < gridY; gy++) {
                        int y = gridMinY + gy * CELL_HEIGHT;
                        int index = index(gx, gy, gz);
                        CavePoint point = caveNoise.sample(x, y, z);
                        caves[index] = point.caves - state.karstModel().caveOpeningBias(x, z);
                        noodleToggle[index] = point.noodleToggle;
                        noodleThickness[index] = point.noodleThickness;
                        noodleRidgeA[index] = point.noodleRidgeA;
                        noodleRidgeB[index] = point.noodleRidgeB;
                    }
                }
            }
        }

        private double sample(int localX, int y, int localZ) {
            double cave = interpolate(caves, localX, y, localZ);
            double toggle = interpolate(noodleToggle, localX, y, localZ);
            if (toggle >= 0.0D) {
                double thicknessNoise = interpolate(noodleThickness, localX, y, localZ);
                double thickness = Mth.clampedMap(thicknessNoise, -1.0D, 1.0D, 0.05D, 0.1D);
                double ridgeA = Math.abs(1.5D * interpolate(noodleRidgeA, localX, y, localZ)) - thickness;
                double ridgeB = Math.abs(1.5D * interpolate(noodleRidgeB, localX, y, localZ)) - thickness;
                cave = Math.min(cave, Math.max(ridgeA, ridgeB));
            }
            return cave;
        }

        private double interpolate(double[] values, int localX, int y, int localZ) {
            int gx = Math.min(3, Math.floorDiv(localX, CELL_WIDTH));
            int gz = Math.min(3, Math.floorDiv(localZ, CELL_WIDTH));
            int gy = Math.clamp(Math.floorDiv(y - gridMinY, CELL_HEIGHT), 0, gridY - 2);
            double dx = Math.floorMod(localX, CELL_WIDTH) / (double) CELL_WIDTH;
            double dz = Math.floorMod(localZ, CELL_WIDTH) / (double) CELL_WIDTH;
            double dy = Math.floorMod(y - gridMinY, CELL_HEIGHT) / (double) CELL_HEIGHT;

            double y00 = Mth.lerp(dy, values[index(gx, gy, gz)], values[index(gx, gy + 1, gz)]);
            double y10 = Mth.lerp(dy, values[index(gx + 1, gy, gz)], values[index(gx + 1, gy + 1, gz)]);
            double y01 = Mth.lerp(dy, values[index(gx, gy, gz + 1)], values[index(gx, gy + 1, gz + 1)]);
            double y11 = Mth.lerp(dy, values[index(gx + 1, gy, gz + 1)], values[index(gx + 1, gy + 1, gz + 1)]);
            return Mth.lerp(dz, Mth.lerp(dx, y00, y10), Mth.lerp(dx, y01, y11));
        }

        private int index(int gx, int gy, int gz) {
            return (gy * GRID_XZ + gz) * GRID_XZ + gx;
        }
    }

    /** Java form of TFC's noise_caves plus noodle density graph. */
    private static final class CaveNoise {
        private final NormalNoise caveCheese;
        private final NormalNoise caveLayer;
        private final NormalNoise caveEntrance;
        private final NormalNoise spaghettiRoughness;
        private final NormalNoise spaghettiRoughnessModulator;
        private final NormalNoise spaghetti2d;
        private final NormalNoise spaghetti2dElevation;
        private final NormalNoise spaghetti2dModulator;
        private final NormalNoise spaghetti2dThickness;
        private final NormalNoise spaghetti3d1;
        private final NormalNoise spaghetti3d2;
        private final NormalNoise spaghetti3dRarity;
        private final NormalNoise spaghetti3dThickness;
        private final NormalNoise noodle;
        private final NormalNoise noodleThickness;
        private final NormalNoise noodleRidgeA;
        private final NormalNoise noodleRidgeB;

        private CaveNoise(RandomState state) {
            caveCheese = state.getOrCreateNoise(Noises.CAVE_CHEESE);
            caveLayer = state.getOrCreateNoise(Noises.CAVE_LAYER);
            caveEntrance = state.getOrCreateNoise(Noises.CAVE_ENTRANCE);
            spaghettiRoughness = state.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS);
            spaghettiRoughnessModulator = state.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS_MODULATOR);
            spaghetti2d = state.getOrCreateNoise(Noises.SPAGHETTI_2D);
            spaghetti2dElevation = state.getOrCreateNoise(Noises.SPAGHETTI_2D_ELEVATION);
            spaghetti2dModulator = state.getOrCreateNoise(Noises.SPAGHETTI_2D_MODULATOR);
            spaghetti2dThickness = state.getOrCreateNoise(Noises.SPAGHETTI_2D_THICKNESS);
            spaghetti3d1 = state.getOrCreateNoise(Noises.SPAGHETTI_3D_1);
            spaghetti3d2 = state.getOrCreateNoise(Noises.SPAGHETTI_3D_2);
            spaghetti3dRarity = state.getOrCreateNoise(Noises.SPAGHETTI_3D_RARITY);
            spaghetti3dThickness = state.getOrCreateNoise(Noises.SPAGHETTI_3D_THICKNESS);
            noodle = state.getOrCreateNoise(Noises.NOODLE);
            noodleThickness = state.getOrCreateNoise(Noises.NOODLE_THICKNESS);
            noodleRidgeA = state.getOrCreateNoise(Noises.NOODLE_RIDGE_A);
            noodleRidgeB = state.getOrCreateNoise(Noises.NOODLE_RIDGE_B);
        }

        private CavePoint sample(int x, int y, int z) {
            double cheese = Mth.clamp(0.27D + caveCheese.getValue(x, y * (2.0D / 3.0D), z), -1.0D, 1.0D);
            double layer = caveLayer.getValue(x, y * 8.0D, z);
            double layeredCheese = cheese + 4.0D * layer * layer;

            double roughness = (0.4D - Math.abs(spaghettiRoughness.getValue(x, y, z)))
                    * (0.05D + 0.05D * spaghettiRoughnessModulator.getValue(x, y, z));
            double spaghetti = Math.min(spaghetti2d(x, y, z), spaghetti3d(x, y, z));
            double entranceGradient = clampedGradient(y, -10, 30, 0.3D, 0.0D);
            double entrance = entranceGradient + 0.37D + caveEntrance.getValue(x * 0.75D, y * 0.5D, z * 0.75D);
            double cave = Mth.clamp(Math.min(layeredCheese, Math.min(roughness + spaghetti, entrance)), -1.0D, 1.0D);

            double highFade = clampedGradient(y, 20, 320, 0.0D, 1.0D);
            cave = cave * (1.0D - highFade) + 2.5D * highFade;

            if (y < -60 || y >= 320) {
                return new CavePoint(cave, -1.0D, 0.0D, 1.0D, 1.0D);
            }
            return new CavePoint(
                    cave,
                    noodle.getValue(x, y, z),
                    noodleThickness.getValue(x, y, z),
                    noodleRidgeA.getValue(x * (8.0D / 3.0D), y * (8.0D / 3.0D), z * (8.0D / 3.0D)),
                    noodleRidgeB.getValue(x * (8.0D / 3.0D), y * (8.0D / 3.0D), z * (8.0D / 3.0D))
            );
        }

        private double spaghetti2d(int x, int y, int z) {
            double rarity = rarity2d(spaghetti2dModulator.getValue(x * 2.0D, y, z * 2.0D));
            double thickness = 0.95D + 0.35D * spaghetti2dThickness.getValue(x * 2.0D, y, z * 2.0D);
            double elevation = 8.0D * spaghetti2dElevation.getValue(x, 0.0D, z);
            double left = Math.abs(elevation - y / 8.0D) - thickness;
            double right = Math.abs(rarity * spaghetti2d.getValue(x / rarity, y / rarity, z / rarity))
                    - 0.083D * thickness;
            return Mth.clamp(Math.max(left * left * left, right), -1.0D, 1.0D);
        }

        private double spaghetti3d(int x, int y, int z) {
            double rarity = rarity3d(spaghetti3dRarity.getValue(x * 2.0D, y, z * 2.0D));
            double thickness = 0.0765D + 0.0115D * spaghetti3dThickness.getValue(x, y, z);
            double value1 = spaghetti3d1.getValue(x / rarity, y / rarity, z / rarity);
            double value2 = spaghetti3d2.getValue(x / rarity, y / rarity, z / rarity);
            return Mth.clamp(Math.max(Math.abs(rarity * value1), Math.abs(rarity * value2)) - thickness, -1.0D, 1.0D);
        }

        private static double rarity2d(double value) {
            if (value < -0.75D) return 0.5D;
            if (value < -0.5D) return 0.75D;
            if (value < 0.5D) return 1.0D;
            return value < 0.75D ? 2.0D : 3.0D;
        }

        private static double rarity3d(double value) {
            if (value < -0.5D) return 0.75D;
            if (value < 0.0D) return 1.0D;
            return value < 0.5D ? 1.5D : 2.0D;
        }

        private static double clampedGradient(int y, int fromY, int toY, double from, double to) {
            return Mth.clampedLerp(from, to, (y - fromY) / (double) (toY - fromY));
        }
    }

    private record CavePoint(
            double caves,
            double noodleToggle,
            double noodleThickness,
            double noodleRidgeA,
            double noodleRidgeB
    ) {
    }

    /**
     * TFC-style cellular aquifer. Every void is classified independently from
     * cave shape, so underwater caves do not become dry ravines.
     */
    private final class EarthAquifer implements Aquifer {
        private static final int GRID_WIDTH = 16;
        private static final int GRID_HEIGHT = 12;
        private static final int XZ_RANGE = 10;
        private static final int Y_RANGE = 8;
        private static final double FLOW_UPDATE_SIMILARITY = similarity(10 * 10, 12 * 12);

        private final int minY;
        private final int seaLevel;
        private final Map<Long, Long> locations = new HashMap<>();
        private final Map<Long, AquiferEntry> entries = new HashMap<>();
        private final int minChunkX;
        private final int minChunkZ;
        private final int[] safeSurfaceHeights;
        private final AquiferEntry lavaLevel;
        private final AquiferEntry seaLevelWater;
        private boolean shouldScheduleFluidUpdate;

        private EarthAquifer(ChunkAccess chunk) {
            this.minY = chunk.getMinBuildHeight();
            this.seaLevel = EarthTerrainProfiles.SEA_LEVEL;
            this.minChunkX = chunk.getPos().getMinBlockX();
            this.minChunkZ = chunk.getPos().getMinBlockZ();
            this.lavaLevel = new AquiferEntry(Blocks.LAVA.defaultBlockState(), minY + 10);
            // EarthNoiseFiller places the top ocean source at SEA_LEVEL itself,
            // so the carver aquifer must use the same inclusive top block.
            this.seaLevelWater = new AquiferEntry(Blocks.WATER.defaultBlockState(), seaLevel + 1);
            this.safeSurfaceHeights = sampleSafeSurfaceHeights();
        }

        @Override
        public BlockState computeSubstance(DensityFunction.FunctionContext context, double terrainDensity) {
            return sampleState(context.blockX(), context.blockY(), context.blockZ(), terrainDensity);
        }

        @Override
        public boolean shouldScheduleFluidUpdate() {
            return shouldScheduleFluidUpdate;
        }

        private BlockState sampleState(int x, int y, int z, double terrainDensity) {
            if (terrainDensity > 0.0D) {
                shouldScheduleFluidUpdate = false;
                return null;
            }

            AquiferEntry global = globalAquifer(y);
            if (global.state.is(Blocks.LAVA)) {
                shouldScheduleFluidUpdate = false;
                return global.at(y);
            }

            EarthRegion.TerrainProfile profile = state.terrainProfiles().profileAtBlock(x, z);
            if (profile == EarthRegion.TerrainProfile.OCEAN
                    || profile == EarthRegion.TerrainProfile.DEEP_OCEAN) {
                /*
                 * A void directly below the ocean is hydraulically connected
                 * to sea water. Cellular dry aquifer entries must not create
                 * giant breathable chambers beneath a leaking stone roof.
                 */
                shouldScheduleFluidUpdate = true;
                return seaLevelWater.at(y);
            }

            int lowerGridX = Math.floorDiv(x - XZ_RANGE / 2, GRID_WIDTH);
            int lowerGridY = Math.floorDiv(y, GRID_HEIGHT);
            int lowerGridZ = Math.floorDiv(z - XZ_RANGE / 2, GRID_WIDTH);

            int distance1 = Integer.MAX_VALUE;
            int distance2 = Integer.MAX_VALUE;
            int distance3 = Integer.MAX_VALUE;
            long aquifer1 = 0L;
            long aquifer2 = 0L;
            long aquifer3 = 0L;

            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        int gridX = lowerGridX + dx;
                        int gridY = lowerGridY + dy;
                        int gridZ = lowerGridZ + dz;
                        long location = location(gridX, gridY, gridZ);
                        int offsetX = BlockPos.getX(location) - x;
                        int offsetY = BlockPos.getY(location) - y;
                        int offsetZ = BlockPos.getZ(location) - z;
                        int distance = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;

                        if (distance <= distance1) {
                            aquifer3 = aquifer2;
                            aquifer2 = aquifer1;
                            aquifer1 = location;
                            distance3 = distance2;
                            distance2 = distance1;
                            distance1 = distance;
                        } else if (distance <= distance2) {
                            aquifer3 = aquifer2;
                            aquifer2 = location;
                            distance3 = distance2;
                            distance2 = distance;
                        } else if (distance <= distance3) {
                            aquifer3 = location;
                            distance3 = distance;
                        }
                    }
                }
            }

            AquiferEntry entry1 = entry(aquifer1);
            AquiferEntry entry2 = entry(aquifer2);
            AquiferEntry entry3 = entry(aquifer3);
            double similarity12 = similarity(distance1, distance2);
            double similarity13 = similarity(distance1, distance3);
            double similarity23 = similarity(distance2, distance3);
            double barrier = 0.0D;

            if (entry1.at(y).is(Blocks.WATER) && globalAquifer(y - 1).at(y - 1).is(Blocks.LAVA)) {
                barrier = 1.0D;
            } else if (similarity12 > -1.0D) {
                double noise = barrierNoise.getValue(x, y * 0.5D, z);
                double pressure12 = pressure(y, noise, entry1, entry2);
                double pressure13 = pressure(y, noise, entry1, entry3);
                double pressure23 = pressure(y, noise, entry2, entry3);
                barrier = Math.max(0.0D, 2.0D * Math.max(0.0D, similarity12)
                        * Math.max(pressure12, Math.max(
                                pressure13 * Math.max(0.0D, similarity13),
                                pressure23 * Math.max(0.0D, similarity23)
                        )));
            }

            shouldScheduleFluidUpdate = similarity12 >= FLOW_UPDATE_SIMILARITY;
            return terrainDensity + barrier <= 0.0D ? entry1.at(y) : null;
        }

        private long location(int gridX, int gridY, int gridZ) {
            long key = BlockPos.asLong(gridX, gridY, gridZ);
            return locations.computeIfAbsent(key, ignored -> {
                RandomSource random = aquiferRandom.at(gridX, gridY, gridZ);
                return BlockPos.asLong(
                        gridX * GRID_WIDTH + random.nextInt(XZ_RANGE) + (GRID_WIDTH - XZ_RANGE) / 2,
                        gridY * GRID_HEIGHT + random.nextInt(Y_RANGE) + (GRID_HEIGHT - Y_RANGE) / 2,
                        gridZ * GRID_WIDTH + random.nextInt(XZ_RANGE) + (GRID_WIDTH - XZ_RANGE) / 2
                );
            });
        }

        private AquiferEntry entry(long location) {
            return entries.computeIfAbsent(location, ignored -> createEntry(
                    BlockPos.getX(location),
                    BlockPos.getY(location),
                    BlockPos.getZ(location)
            ));
        }

        private AquiferEntry createEntry(int x, int y, int z) {
            int surface = safeSurfaceHeight(x, z);
            if (y >= surface) {
                return seaLevelWater;
            }

            Cell cell = cellularCell(x, y / 0.6D, z);
            float cellNoise = (float) cell.noise;
            float cellY = (float) cell.y;
            if (cellNoise < 0.25D || (cellY > seaLevel - 10 && cellNoise < 0.5D)) {
                return new AquiferEntry(Blocks.WATER.defaultBlockState(), minY - 1);
            }

            RandomSource random = new XoroshiroRandomSource(fluidCellSeed, Float.floatToIntBits(cellNoise));
            float aquiferY = Math.min((random.nextFloat() - random.nextFloat() - 2.0F) * 5.0F + cellY, surface);
            boolean lava = cellY < 40.0D && random.nextInt(3) == 0;
            return new AquiferEntry(
                    lava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState(),
                    (int) aquiferY
            );
        }

        private int safeSurfaceHeight(int x, int z) {
            int localGridX = Math.clamp(SectionPos.blockToSectionCoord(x - minChunkX + 16), 0, 3);
            int localGridZ = Math.clamp(SectionPos.blockToSectionCoord(z - minChunkZ + 16), 0, 3);
            return safeSurfaceHeights[localGridX + 4 * localGridZ];
        }

        /** TFC's 11x11 half-chunk sampling reduced into 4x4 safe ceilings. */
        private int[] sampleSafeSurfaceHeights() {
            double[] sampled = new double[11 * 11];
            for (int sampleX = 0; sampleX < 11; sampleX++) {
                int x = minChunkX - 32 + sampleX * 8;
                for (int sampleZ = 0; sampleZ < 11; sampleZ++) {
                    int z = minChunkZ - 32 + sampleZ * 8;
                    EarthHeightFiller.ColumnSample column = state.heightFiller().sampleColumn(x, z);
                    double height = column.height();
                    if (column.riverCore() && height > seaLevel - 24) {
                        height = seaLevel - 24;
                    }
                    if (height > seaLevel) {
                        height = 0.3D * seaLevel + 0.7D * height;
                    }
                    sampled[sampleX + 11 * sampleZ] = height;
                }
            }

            int[] output = new int[4 * 4];
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    int centerX = (1 + x) << 1;
                    int centerZ = (1 + z) << 1;
                    double lowest = Double.MAX_VALUE;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            lowest = Math.min(lowest, sampled[(centerX + dx) + 11 * (centerZ + dz)]);
                        }
                    }
                    output[x + 4 * z] = (int) Math.floor(lowest);
                }
            }
            return output;
        }

        /** Independent deterministic cellular sample used for fluid bodies. */
        private Cell cellularCell(double x, double y, double z) {
            final double frequency = 0.015D;
            double scaledX = x * frequency;
            double scaledY = y * frequency;
            double scaledZ = z * frequency;
            int centerX = Mth.floor(scaledX + 0.5D);
            int centerY = Mth.floor(scaledY + 0.5D);
            int centerZ = Mth.floor(scaledZ + 0.5D);
            double bestDistance = Double.MAX_VALUE;
            double bestX = 0.0D;
            double bestY = 0.0D;
            double bestZ = 0.0D;
            long bestHash = 0L;

            for (int cellX = centerX - 1; cellX <= centerX + 1; cellX++) {
                for (int cellY = centerY - 1; cellY <= centerY + 1; cellY++) {
                    for (int cellZ = centerZ - 1; cellZ <= centerZ + 1; cellZ++) {
                        long hash = mix64(fluidCellSeed
                                ^ cellX * 0x9E3779B97F4A7C15L
                                ^ cellY * 0xC2B2AE3D27D4EB4FL
                                ^ cellZ * 0x165667B19E3779F9L);
                        double pointX = cellX + signedUnit(hash) * 0.39614353D;
                        double pointY = cellY + signedUnit(Long.rotateLeft(hash, 21)) * 0.39614353D;
                        double pointZ = cellZ + signedUnit(Long.rotateLeft(hash, 42)) * 0.39614353D;
                        double dx = pointX - scaledX;
                        double dy = pointY - scaledY;
                        double dz = pointZ - scaledZ;
                        double distance = dx * dx + dy * dy + dz * dz;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestX = pointX;
                            bestY = pointY;
                            bestZ = pointZ;
                            bestHash = hash;
                        }
                    }
                }
            }
            return new Cell(
                    bestX / frequency,
                    bestY / frequency,
                    bestZ / frequency,
                    signedUnit(bestHash)
            );
        }

        private AquiferEntry globalAquifer(int y) {
            return y < minY + 10 ? lavaLevel : seaLevelWater;
        }

        private static double pressure(int y, double barrierNoise, AquiferEntry left, AquiferEntry right) {
            BlockState leftState = left.at(y);
            BlockState rightState = right.at(y);
            if ((leftState.is(Blocks.LAVA) && rightState.is(Blocks.WATER))
                    || (leftState.is(Blocks.WATER) && rightState.is(Blocks.LAVA))) {
                return 1.0D;
            }
            int deltaLevel = Math.abs(left.fluidY - right.fluidY);
            if (deltaLevel == 0) return 0.0D;
            double average = 0.5D * (left.fluidY + right.fluidY);
            double aboveAverage = y + 0.5D - average;
            double nearAverage = 0.5D * deltaLevel - Math.abs(aboveAverage);
            double pressure;
            if (aboveAverage > 0.0D) {
                pressure = nearAverage / (nearAverage > 0.0D ? 1.5D : 2.5D);
            } else {
                pressure = (nearAverage + 3.0D) / (nearAverage > -3.0D ? 3.0D : 10.0D);
            }
            return pressure < -2.0D || pressure > 2.0D ? pressure : pressure + barrierNoise;
        }

        private static double similarity(int first, int second) {
            return 1.0D - Math.abs(second - first) / 25.0D;
        }
    }

    private record AquiferEntry(BlockState state, int fluidY) {
        private BlockState at(int y) {
            return y < fluidY ? state : Blocks.AIR.defaultBlockState();
        }
    }

    private record Cell(double x, double y, double z, double noise) {
    }

    private static double signedUnit(long value) {
        return ((value >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
