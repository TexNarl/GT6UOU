/*
 * Regional architecture follows TerraFirmaCraft/TerraFirmaGreg worldgen.
 * Those projects and their EUPL/LGPL licenses are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.region;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe, seed-bound source of irregular Earth regions.
 *
 * <p>Region cells are approximately 12,288 blocks wide. A queried grid point
 * first resolves its cellular owner and only then indexes the bounded region
 * cache. This prevents square cache coordinates from becoming geological or
 * continental borders.</p>
 */
public final class EarthRegionGenerator {
    public static final double LAND_THRESHOLD = 4.4;
    public static final int MAX_CACHED_REGIONS = 256;
    public static final int TEMPERATURE_SCALE_BLOCKS = 20_000;
    public static final int RAINFALL_SCALE_BLOCKS = 20_000;

    private final long seed;
    private final CellularNoise cellNoise;
    private final FractalNoise continentShape;
    private final FractalNoise temperatureVariation;
    private final FractalNoise rainfallVariation;
    private final FractalNoise oceanicInfluence;
    private final FractalNoise activeHotSpots;
    private final FractalNoise plateWarp;
    private final ConcurrentHashMap<Long, EarthRegion> regionCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Long> cacheOrder = new ConcurrentLinkedDeque<>();

    public EarthRegionGenerator(long seed) {
        this.seed = seed;
        cellNoise = new CellularNoise(seed, 2, 0.43701595);
        continentShape = new FractalNoise(seed ^ 0x6E624EB7A31B4C91L, 4, 0.24);
        temperatureVariation = new FractalNoise(seed ^ 0x43E96A74D76C47A3L, 2, 0.15);
        rainfallVariation = new FractalNoise(seed ^ 0xB6F154F5D21A13B9L, 2, 0.15);
        oceanicInfluence = new FractalNoise(seed ^ 0x91E10DA5C79E7B1DL, 1, 0.02);
        // TFG samples a 0.003 block-space hotspot field after spreading it by
        // one 128-block regional grid unit: 0.003 * 128 = 0.384.
        activeHotSpots = new FractalNoise(seed ^ 0xD1B54A32D192ED03L, 3, 0.384);
        plateWarp = new FractalNoise(seed ^ 0x94D049BB133111EBL, 1, 0.011);
    }

    public EarthRegion.Point getOrCreatePoint(int gridX, int gridZ) {
        return getOrCreateRegion(gridX, gridZ).atOrThrow(gridX, gridZ);
    }

    public EarthRegion getOrCreateRegion(int gridX, int gridZ) {
        return getOrCreateRegion(sampleCell(gridX, gridZ));
    }

    /**
     * Returns the irregular region owned by one coarse TFC-scale cell. River
     * partitions use this to gather the nine owner regions which can overlap
     * a partition cell without turning those square cache cells into terrain
     * borders.
     */
    public EarthRegion getOrCreateRegionForOwner(int ownerX, int ownerZ) {
        return getOrCreateRegion(cellNoise.owner(ownerX, ownerZ));
    }

    public Cell sampleCell(double gridX, double gridZ) {
        return cellNoise.sample(gridX, gridZ);
    }

    public RawSample sampleRaw(double gridX, double gridZ) {
        Cell cell = sampleCell(gridX, gridZ);
        return new RawSample(cell, rawContinent(gridX, gridZ, cell));
    }

    /**
     * Samples the completed regional task result. A regional grid point covers
     * 128x128 blocks, matching TFG's pre-zoom semantic map.
     */
    public EarthRegion.Point sampleSemantic(double gridX, double gridZ) {
        return getOrCreatePoint((int) Math.floor(gridX), (int) Math.floor(gridZ));
    }

    public double rawContinent(double gridX, double gridZ) {
        Cell cell = sampleCell(gridX, gridZ);
        return rawContinent(gridX, gridZ, cell);
    }

    /** TFC/TFG latitude triangle plus its two-octave regional variation. */
    double baseTemperature(int gridX, int gridZ) {
        double latitude = triangle(false, TEMPERATURE_SCALE_BLOCKS, gridX, gridZ);
        return map(latitude, -1.0, 1.0, -20.0, 30.0)
                + map(temperatureVariation.sample(gridX, gridZ), -1.0, 1.0, -3.0, 3.0);
    }

    /** TFC/TFG longitude rainfall triangle plus its dry-biased variation. */
    double baseRainfall(int gridX, int gridZ) {
        double longitude = triangle(true, RAINFALL_SCALE_BLOCKS, gridX, gridZ);
        return map(longitude, -1.0, 1.0, 0.0, 500.0)
                + map(rainfallVariation.sample(gridX, gridZ), -1.0, 1.0, -80.0, 40.0);
    }

    double oceanicInfluence(int gridX, int gridZ) {
        return oceanicInfluence.sample(gridX, gridZ);
    }

    double hotSpotIntensity(double gridX, double gridZ) {
        double warp = plateWarp.sample(gridX, gridZ);
        double warpZ = (Math.abs(warp * 16.0) % 1.0 > 0.5 ? 1.0 : -1.0)
                * ((warp * 256.0) % 1.0);
        double active = activeHotSpot(gridX, gridZ);
        double dormant = Math.max(activeHotSpot(
                gridX + (warp + Math.copySign(1.0, warp)) * 8.0,
                gridZ + (warpZ + Math.copySign(1.0, warpZ)) * 8.0
        ) - 0.1, 0.0) * 1.111;
        double extinct = Math.max(activeHotSpot(
                gridX + (warp + Math.copySign(1.0, warp)) * 16.0 - warpZ * 4.0,
                gridZ + (warpZ + Math.copySign(1.0, warpZ)) * 16.0 + warp * 4.0
        ) - 0.2, 0.0) * 1.25;
        double ancient = Math.max(activeHotSpot(
                gridX + (warp + Math.copySign(1.0, warp)) * 24.0 - warpZ * 12.0,
                gridZ + (warpZ + Math.copySign(1.0, warpZ)) * 24.0 + warp * 12.0
        ) - 0.3, 0.0) * 1.4286;
        return Math.max(Math.max(active, dormant), Math.max(extinct, ancient));
    }

    byte hotSpotAge(double gridX, double gridZ) {
        double warp = plateWarp.sample(gridX, gridZ);
        double warpZ = (Math.abs(warp * 16.0) % 1.0 > 0.5 ? 1.0 : -1.0)
                * ((warp * 256.0) % 1.0);
        double[] values = {
                activeHotSpot(gridX, gridZ),
                activeHotSpot(gridX + (warp + Math.copySign(1.0, warp)) * 8.0,
                        gridZ + (warpZ + Math.copySign(1.0, warpZ)) * 8.0),
                activeHotSpot(gridX + (warp + Math.copySign(1.0, warp)) * 16.0 - warpZ * 4.0,
                        gridZ + (warpZ + Math.copySign(1.0, warpZ)) * 16.0 + warp * 4.0),
                activeHotSpot(gridX + (warp + Math.copySign(1.0, warp)) * 24.0 - warpZ * 12.0,
                        gridZ + (warpZ + Math.copySign(1.0, warpZ)) * 24.0 + warp * 12.0)
        };
        int best = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[best]) best = index;
        }
        return (byte) (best + 1);
    }

    private double activeHotSpot(double gridX, double gridZ) {
        double value = activeHotSpots.sample(gridX, gridZ);
        return value > 0.75 ? (value - 0.75) * 7.2 : 0.0;
    }

    public int cachedRegionCount() {
        return regionCache.size();
    }

    XoroshiroRandomSource createRegionRandom(Cell owner) {
        long regionSeed = seed ^ Float.floatToIntBits((float) owner.noise()) * 7189234123L;
        return new XoroshiroRandomSource(regionSeed);
    }

    int pointHash(int gridX, int gridZ, long salt) {
        long value = seed ^ salt;
        value ^= gridX * 0x9E3779B97F4A7C15L;
        value ^= gridZ * 0xC2B2AE3D27D4EB4FL;
        return mix32(value);
    }

    private EarthRegion getOrCreateRegion(Cell owner) {
        long key = pack(owner.ownerX(), owner.ownerZ());
        EarthRegion region = regionCache.computeIfAbsent(key, ignored -> {
            EarthRegion generated = EarthRegionTasks.generate(this, owner);
            cacheOrder.addLast(key);
            return generated;
        });
        trimCache();
        return region;
    }

    private double rawContinent(double gridX, double gridZ, Cell cell) {
        double cellularValue = 1.0 - cell.f1() / (0.37 + cell.f2());
        double simplex = continentShape.sample(gridX, gridZ);
        double scaledShape = 2.5 + (simplex + 1.0) * 0.5 * (8.7 - 2.5);
        return cellularValue * scaledShape;
    }

    private static double triangle(boolean axisIsX, int scale, int gridX, int gridZ) {
        double frequency = EarthUnits.GRID_WIDTH_IN_BLOCK / (2.0 * scale);
        double value = axisIsX ? gridX : gridZ;
        return Math.abs(4.0 * frequency * value + 1.0
                - 4.0 * Math.floor(frequency * value + 0.75)) - 1.0;
    }

    private static double map(double value, double oldMin, double oldMax, double newMin, double newMax) {
        return newMin + (value - oldMin) * (newMax - newMin) / (oldMax - oldMin);
    }

    private void trimCache() {
        while (regionCache.size() > MAX_CACHED_REGIONS) {
            Long oldest = cacheOrder.pollFirst();
            if (oldest == null) {
                return;
            }
            regionCache.remove(oldest);
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int mix32(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (int) (value ^ (value >>> 32));
    }

    public record Cell(
            int ownerX,
            int ownerZ,
            double centerX,
            double centerZ,
            double f1,
            double f2,
            double noise
    ) {
        public boolean sameOwner(Cell other) {
            return ownerX == other.ownerX && ownerZ == other.ownerZ;
        }
    }

    public record RawSample(Cell cell, double continent) {
        public boolean land() {
            return continent > LAND_THRESHOLD;
        }
    }

    private static final class CellularNoise {
        private static final int VECTOR_COUNT = 256;
        private static final double[] VECTOR_X = new double[VECTOR_COUNT];
        private static final double[] VECTOR_Z = new double[VECTOR_COUNT];

        static {
            double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
            for (int i = 0; i < VECTOR_COUNT; i++) {
                double angle = i * goldenAngle;
                VECTOR_X[i] = Math.cos(angle);
                VECTOR_Z[i] = Math.sin(angle);
            }
        }

        private final long seed;
        private final int radius;
        private final double jitter;
        private final double frequency = 1.0 / EarthUnits.CELL_WIDTH_IN_GRID;

        private CellularNoise(long seed, int radius, double jitter) {
            this.seed = seed;
            this.radius = radius;
            this.jitter = jitter;
        }

        private Cell sample(double gridX, double gridZ) {
            double x = gridX * frequency;
            double z = gridZ * frequency;
            int floorX = (int) Math.floor(x);
            int floorZ = (int) Math.floor(z);

            double nearest = Double.MAX_VALUE;
            double second = Double.MAX_VALUE;
            double nearestCenterX = 0;
            double nearestCenterZ = 0;
            int nearestOwnerX = 0;
            int nearestOwnerZ = 0;
            int nearestHash = 0;

            for (int cellX = floorX - radius; cellX <= floorX + radius; cellX++) {
                for (int cellZ = floorZ - radius; cellZ <= floorZ + radius; cellZ++) {
                    int hash = mix32(seed ^ (cellX * 501125321L) ^ (cellZ * 1136930381L));
                    int vector = hash & 255;
                    double centerX = cellX + VECTOR_X[vector] * jitter;
                    double centerZ = cellZ + VECTOR_Z[vector] * jitter;
                    double dx = centerX - x;
                    double dz = centerZ - z;
                    double distance = dx * dx + dz * dz;

                    if (distance < nearest) {
                        second = nearest;
                        nearest = distance;
                        nearestCenterX = centerX;
                        nearestCenterZ = centerZ;
                        nearestOwnerX = cellX;
                        nearestOwnerZ = cellZ;
                        nearestHash = hash;
                    } else if (distance < second) {
                        second = distance;
                    }
                }
            }

            return new Cell(
                    nearestOwnerX,
                    nearestOwnerZ,
                    nearestCenterX / frequency,
                    nearestCenterZ / frequency,
                    nearest,
                    second,
                    nearestHash / 2147483648.0
            );
        }

        private Cell owner(int ownerX, int ownerZ) {
            int hash = mix32(seed ^ (ownerX * 501125321L) ^ (ownerZ * 1136930381L));
            int vector = hash & 255;
            double centerX = (ownerX + VECTOR_X[vector] * jitter) / frequency;
            double centerZ = (ownerZ + VECTOR_Z[vector] * jitter) / frequency;
            return new Cell(ownerX, ownerZ, centerX, centerZ, 0.0, 0.0, hash / 2147483648.0);
        }
    }

    private static final class FractalNoise {
        private final SimplexNoise[] octaves;
        private final double spread;
        private final double amplitudeSum;

        private FractalNoise(long seed, int octaveCount, double spread) {
            XoroshiroRandomSource random = new XoroshiroRandomSource(seed);
            octaves = new SimplexNoise[octaveCount];
            double amplitude = 1.0;
            double sum = 0.0;
            for (int i = 0; i < octaveCount; i++) {
                octaves[i] = new SimplexNoise(random);
                sum += amplitude;
                amplitude *= 0.5;
            }
            this.spread = spread;
            this.amplitudeSum = sum;
        }

        private double sample(double x, double z) {
            double frequency = spread;
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
