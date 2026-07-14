/*
 * Layer order and profile-noise formulas are adapted from TerraFirmaCraft and
 * TerraFirmaGreg. Those projects and their licenses are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.terrain;

import gregtech.worldgen.earth.region.EarthRegion;
import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import gregtech.worldgen.earth.region.EarthRegionGenerator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Seed-bound terrain profile table and TFG-style grid-to-quart layer stack.
 *
 * <p>The semantic region map is sampled at one point per 128 blocks. It is
 * passed through the same structural stages as TFG: one regional edge pass,
 * five normal zooms and a final adjacent smoothing pass. The result therefore
 * reaches Minecraft's quart scale without introducing a second continent or
 * mountain selector.</p>
 */
public final class EarthTerrainProfiles {
    public static final int SEA_LEVEL = 63;
    private static final TerrainProfile[] ALL_PROFILES = TerrainProfile.values();

    private final ProfileNoise[] noises = new ProfileNoise[ALL_PROFILES.length];
    private final TerrainLayer layer;

    public EarthTerrainProfiles(EarthRegionGenerator regionGenerator, long terrainSeed) {
        XoroshiroRandomSource seeds = new XoroshiroRandomSource(terrainSeed);
        for (TerrainProfile profile : ALL_PROFILES) {
            noises[profile.ordinal()] = createNoise(profile, seeds.nextLong());
        }
        layer = new TerrainLayer(regionGenerator, terrainSeed);
    }

    /** Returns the final, zoomed and smoothed profile at a block position. */
    public TerrainProfile profileAtBlock(int blockX, int blockZ) {
        return layer.sampleQuart(blockX >> 2, blockZ >> 2);
    }

    /** Returns the TFG-style height noise belonging to one terrain profile. */
    public double height(TerrainProfile profile, int blockX, int blockZ) {
        return noises[profile.ordinal()].sample(blockX, blockZ);
    }

    private static ProfileNoise createNoise(TerrainProfile profile, long seed) {
        return switch (profile) {
            case DEEP_OCEAN -> ocean(seed, -30, -16);
            case OCEAN -> ocean(seed, -26, -12);
            case ISLAND -> mountains(seed, -24, 50);
            case PLAINS -> hills(seed, 4, 10);
            case HILLS -> hills(seed, -5, 16);
            case ROLLING_HILLS -> hills(seed, -5, 28);
            case HIGHLANDS -> sharpHills(seed, -3, 28);
            case MOUNTAINS -> mountains(seed, 10, 70);
            case COASTAL_MOUNTAINS -> mountains(seed, -16, 60);
            case SHORE, TIDAL_FLATS, EMBAYMENTS -> hills(seed, 0, 5);
            case COASTAL_DUNES -> hills(seed, 0, 2);
            case ROCKY_SHORES -> constant(SEA_LEVEL - 15.0);
            case LAKE -> hills(seed, -8, -3);
            case VOLCANO -> mountains(seed, -8, 58);
        };
    }

    private static ProfileNoise constant(double height) {
        return (x, z) -> height;
    }

    private static ProfileNoise hills(long seed, int minHeight, int maxHeight) {
        FractalSimplex noise = new FractalSimplex(seed, 4, 0.05);
        double min = SEA_LEVEL + minHeight;
        double range = maxHeight - minHeight;
        return (x, z) -> min + (noise.sample(x, z) + 1.0) * 0.5 * range;
    }

    private static ProfileNoise ocean(long seed, int minDepth, int maxDepth) {
        FractalSimplex warpX = new FractalSimplex(seed, 2, 0.015);
        FractalSimplex warpZ = new FractalSimplex(seed + 0x632BE59BD9B4E019L, 2, 0.015);
        FractalSimplex floor = new FractalSimplex(seed + 1, 4, 0.11);
        double min = SEA_LEVEL + minDepth;
        double range = maxDepth - minDepth;
        return (x, z) -> {
            double warpedX = x + warpX.sample(x, z) * 30.0;
            double warpedZ = z + warpZ.sample(x, z) * 30.0;
            return min + (floor.sample(warpedX, warpedZ) + 1.0) * 0.5 * range;
        };
    }

    private static ProfileNoise sharpHills(long seed, int minHeight, int maxHeight) {
        FractalSimplex base = new FractalSimplex(seed, 4, 0.08);
        FractalSimplex blend = new FractalSimplex(seed + 7_198_234_123L, 1, 0.013);
        FractalSimplex variance = new FractalSimplex(seed + 67_981_832_123L, 3, 0.06);
        // TFC/TFG sharpHills deliberately subtracts the named minimum
        // parameter (unlike the regular hills helper). With -3 this is Y=66,
        // not Y=60; using addition exaggerated troughs beside high terrain.
        double outputMin = SEA_LEVEL - minHeight;
        double outputRange = maxHeight - minHeight;
        return (x, z) -> {
            double input = base.sample(x, z);
            double t = Math.clamp((blend.sample(x, z) + 1.0) * 0.95 - 0.3, 0.0, 1.0);
            double shaped = lerp(t, input, sharpHillsMap(input)) + variance.sample(x, z) * 0.2;
            double normalized = Math.clamp((shaped + 0.75) / 1.45, 0.0, 1.0);
            return outputMin + normalized * outputRange;
        };
    }

    private static ProfileNoise mountains(long seed, int baseHeight, int scaleHeight) {
        FractalSimplex base = new FractalSimplex(seed, 6, 0.14);
        FractalSimplex ridges = new FractalSimplex(seed + 1, 4, 0.02);
        FractalSimplex cliffs = new FractalSimplex(seed + 2, 2, 0.01);
        FractalSimplex cliffHeight = new FractalSimplex(seed + 3, 2, 0.01);
        return (x, z) -> {
            double ridge = (1.0 - 2.0 * Math.abs(ridges.sample(x, z))) * 0.9;
            double combined = base.sample(x, z) + ridge;
            double cubic = 0.125 * Math.pow(combined + 1.0, 3.0);
            double height = SEA_LEVEL + baseHeight + scaleHeight * cubic;

            if (height > 120.0) {
                double threshold = 140.0 + cliffHeight.sample(x, z) * 20.0;
                if (height > threshold) {
                    height += Math.max(0.0, cliffs.sample(x, z) * 25.0);
                }
            }
            return Math.clamp(height, -48.0, 300.0);
        };
    }

    private static double sharpHillsMap(double value) {
        if (value > 0.67) return map(value, 0.67, 1.0, 0.7, 1.0);
        if (value > 0.15) return map(value, 0.15, 0.67, 0.5, 0.7);
        if (value > -0.15) return map(value, -0.15, 0.15, -0.5, 0.5);
        if (value > -0.67) return map(value, -0.67, -0.15, -0.7, -0.5);
        return map(value, -1.0, -0.67, -1.0, -0.7);
    }

    private static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    @FunctionalInterface
    private interface ProfileNoise {
        double sample(double x, double z);
    }

    /**
     * Minecraft supplies a thread-safe immutable simplex primitive. This
     * wrapper reproduces TFC's octave frequency order while avoiding a second
     * 100 KiB copy of FastNoiseLite in the port.
     */
    private static final class FractalSimplex {
        private final SimplexNoise[] octaves;
        private final double initialFrequency;
        private final double amplitudeSum;

        private FractalSimplex(long seed, int octaveCount, double spread) {
            XoroshiroRandomSource random = new XoroshiroRandomSource(seed);
            octaves = new SimplexNoise[octaveCount];
            double amplitude = 1.0;
            double sum = 0.0;
            for (int i = 0; i < octaveCount; i++) {
                octaves[i] = new SimplexNoise(random);
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

    /** Exact structural equivalent of TFG's edge -> 5 zooms -> smooth chain. */
    private static final class TerrainLayer {
        private static final int ZOOM_COUNT = 5;
        private static final int CACHE_SIZE = 1 << 14;
        private static final int TINY_ISLAND_SEARCH_RADIUS = 10;
        private static final int TINY_ISLAND_MAX_QUARTS = 160;

        private final EarthRegionGenerator regions;
        private final long edgeSeed;
        private final long[] zoomSeeds = new long[ZOOM_COUNT];
        private final long smoothSeed;
        private final ThreadLocal<LayerCaches> caches = ThreadLocal.withInitial(LayerCaches::new);

        private TerrainLayer(EarthRegionGenerator regions, long seed) {
            this.regions = regions;
            XoroshiroRandomSource sequence = new XoroshiroRandomSource(seed);
            sequence.nextLong(); // TFG RegionLayer seed
            edgeSeed = sequence.nextLong();
            zoomSeeds[0] = sequence.nextLong();
            sequence.nextLong(); // TFG shore layer seed (adjacent transform is deterministic)
            sequence.nextLong(); // TFG extra-shore layer seed
            sequence.nextLong(); // ice edge layer, intentionally climate-deferred
            for (int stage = 1; stage < ZOOM_COUNT; stage++) {
                zoomSeeds[stage] = sequence.nextLong();
            }
            smoothSeed = sequence.nextLong();
        }

        private TerrainProfile sampleQuart(int quartX, int quartZ) {
            LayerCaches local = caches.get();
            int value = local.cleaned.getOrCompute(quartX, quartZ, (x, z) -> removeTinyIsland(local, x, z));
            return ALL_PROFILES[value];
        }

        /**
         * Zoom can leave a handful of detached 4x4-block land pixels beside a
         * coast. Remove only components which are completely enclosed inside
         * this local search window and remain tiny. A peninsula or a normal
         * island reaches the window edge / size limit and is preserved.
         */
        private int removeTinyIsland(LayerCaches local, int originX, int originZ) {
            int center = smoothCached(local, originX, originZ);
            if (isOcean(ALL_PROFILES[center])) {
                return center;
            }

            int diameter = 1 + 2 * TINY_ISLAND_SEARCH_RADIUS;
            boolean[] visited = new boolean[diameter * diameter];
            int[] queueX = new int[diameter * diameter];
            int[] queueZ = new int[diameter * diameter];
            int head = 0;
            int tail = 1;
            queueX[0] = originX;
            queueZ[0] = originZ;
            visited[TINY_ISLAND_SEARCH_RADIUS * diameter + TINY_ISLAND_SEARCH_RADIUS] = true;

            while (head < tail) {
                int x = queueX[head];
                int z = queueZ[head++];
                int localX = x - originX + TINY_ISLAND_SEARCH_RADIUS;
                int localZ = z - originZ + TINY_ISLAND_SEARCH_RADIUS;

                if (localX == 0 || localZ == 0 || localX == diameter - 1 || localZ == diameter - 1
                        || tail >= TINY_ISLAND_MAX_QUARTS) {
                    return center;
                }

                for (int direction = 0; direction < 4; direction++) {
                    int nextX = x + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                    int nextZ = z + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
                    int nextLocalX = nextX - originX + TINY_ISLAND_SEARCH_RADIUS;
                    int nextLocalZ = nextZ - originZ + TINY_ISLAND_SEARCH_RADIUS;
                    int index = nextLocalX + diameter * nextLocalZ;
                    if (!visited[index]
                            && !isOcean(ALL_PROFILES[smoothCached(local, nextX, nextZ)])) {
                        visited[index] = true;
                        queueX[tail] = nextX;
                        queueZ[tail++] = nextZ;
                    }
                }
            }
            for (int index = 0; index < tail; index++) {
                local.cleaned.put(queueX[index], queueZ[index], TerrainProfile.OCEAN.ordinal());
            }
            return TerrainProfile.OCEAN.ordinal();
        }

        private int smoothCached(LayerCaches local, int x, int z) {
            return local.smooth.getOrCompute(x, z, (sampleX, sampleZ) -> smooth(local, sampleX, sampleZ));
        }

        private int smooth(LayerCaches local, int x, int z) {
            int north = zoom(local, ZOOM_COUNT - 1, x, z - 1);
            int east = zoom(local, ZOOM_COUNT - 1, x + 1, z);
            int south = zoom(local, ZOOM_COUNT - 1, x, z + 1);
            int west = zoom(local, ZOOM_COUNT - 1, x - 1, z);
            int center = zoom(local, ZOOM_COUNT - 1, x, z);
            boolean equalX = west == east;
            boolean equalZ = north == south;
            if (equalX == equalZ) {
                return equalX ? choose(smoothSeed, x, z, east, north) : center;
            }
            return equalX ? east : north;
        }

        private int zoom(LayerCaches local, int stage, int x, int z) {
            return local.zooms[stage].getOrCompute(x, z, (sampleX, sampleZ) -> {
                int parentX = sampleX >> 1;
                int parentZ = sampleZ >> 1;
                int offsetX = sampleX & 1;
                int offsetZ = sampleZ & 1;
                int northWest = parent(local, stage, parentX, parentZ);
                if (offsetX == 0 && offsetZ == 0) return northWest;
                if (offsetX == 0) {
                    return choose(zoomSeeds[stage], parentX, parentZ, northWest,
                            parent(local, stage, parentX, parentZ + 1));
                }
                if (offsetZ == 0) {
                    return choose(zoomSeeds[stage], parentX, parentZ, northWest,
                            parent(local, stage, parentX + 1, parentZ));
                }
                return chooseNormal(
                        zoomSeeds[stage], parentX, parentZ,
                        northWest,
                        parent(local, stage, parentX, parentZ + 1),
                        parent(local, stage, parentX + 1, parentZ),
                        parent(local, stage, parentX + 1, parentZ + 1)
                );
            });
        }

        private int parent(LayerCaches local, int stage, int x, int z) {
            if (stage == 0) return edge(local, x, z);
            if (stage == 1) return moreShore(local, x, z);
            return zoom(local, stage - 1, x, z);
        }

        /** TFGShoreLayer, inserted after the first normal zoom. */
        private int shore(LayerCaches local, int x, int z) {
            return local.shore.getOrCompute(x, z, (sampleX, sampleZ) -> {
                TerrainProfile north = ALL_PROFILES[zoom(local, 0, sampleX, sampleZ - 1)];
                TerrainProfile east = ALL_PROFILES[zoom(local, 0, sampleX + 1, sampleZ)];
                TerrainProfile south = ALL_PROFILES[zoom(local, 0, sampleX, sampleZ + 1)];
                TerrainProfile west = ALL_PROFILES[zoom(local, 0, sampleX - 1, sampleZ)];
                TerrainProfile center = ALL_PROFILES[zoom(local, 0, sampleX, sampleZ)];
                if (!isOcean(center) && hasShore(center)
                        && (isOcean(north) || isOcean(east) || isOcean(south) || isOcean(west))) {
                    return shoreFor(center).ordinal();
                }
                return center.ordinal();
            });
        }

        /** TFGMoreShoresLayer, widening only the matching shore family. */
        private int moreShore(LayerCaches local, int x, int z) {
            return local.moreShore.getOrCompute(x, z, (sampleX, sampleZ) -> {
                TerrainProfile north = ALL_PROFILES[shore(local, sampleX, sampleZ - 1)];
                TerrainProfile east = ALL_PROFILES[shore(local, sampleX + 1, sampleZ)];
                TerrainProfile south = ALL_PROFILES[shore(local, sampleX, sampleZ + 1)];
                TerrainProfile west = ALL_PROFILES[shore(local, sampleX - 1, sampleZ)];
                TerrainProfile center = ALL_PROFILES[shore(local, sampleX, sampleZ)];
                if (center != TerrainProfile.OCEAN) {
                    TerrainProfile propagated = propagatedShore(north, east, south, west);
                    if (propagated != null) return propagated.ordinal();
                }
                return center.ordinal();
            });
        }

        private static TerrainProfile propagatedShore(TerrainProfile... adjacent) {
            for (TerrainProfile profile : adjacent) {
                if (profile == TerrainProfile.TIDAL_FLATS || profile == TerrainProfile.SHORE) {
                    return TerrainProfile.SHORE;
                }
            }
            for (TerrainProfile profile : adjacent) {
                if (profile == TerrainProfile.COASTAL_DUNES) return profile;
            }
            for (TerrainProfile profile : adjacent) {
                if (profile == TerrainProfile.ROCKY_SHORES) return profile;
            }
            for (TerrainProfile profile : adjacent) {
                if (profile == TerrainProfile.EMBAYMENTS) return profile;
            }
            return null;
        }

        private static boolean hasShore(TerrainProfile profile) {
            return profile != TerrainProfile.COASTAL_MOUNTAINS;
        }

        private static TerrainProfile shoreFor(TerrainProfile profile) {
            return switch (profile) {
                case HILLS -> TerrainProfile.COASTAL_DUNES;
                case ROLLING_HILLS -> TerrainProfile.EMBAYMENTS;
                case HIGHLANDS -> TerrainProfile.ROCKY_SHORES;
                case MOUNTAINS -> TerrainProfile.COASTAL_MOUNTAINS;
                case PLAINS, ISLAND -> TerrainProfile.TIDAL_FLATS;
                case LAKE -> TerrainProfile.LAKE;
                case VOLCANO -> TerrainProfile.ROCKY_SHORES;
                default -> profile;
            };
        }

        private int edge(LayerCaches local, int x, int z) {
            return local.edge.getOrCompute(x, z, (sampleX, sampleZ) -> {
                TerrainProfile north = source(sampleX, sampleZ - 1);
                TerrainProfile east = source(sampleX + 1, sampleZ);
                TerrainProfile south = source(sampleX, sampleZ + 1);
                TerrainProfile west = source(sampleX - 1, sampleZ);
                TerrainProfile center = source(sampleX, sampleZ);
                TerrainProfile result = transformEdge(north, east, south, west, center);
                return result.ordinal();
            });
        }

        private TerrainProfile source(int gridX, int gridZ) {
            EarthRegion.Point point = regions.getOrCreatePoint(gridX, gridZ);
            return point.terrainProfile();
        }

        private static TerrainProfile transformEdge(
                TerrainProfile north,
                TerrainProfile east,
                TerrainProfile south,
                TerrainProfile west,
                TerrainProfile center
        ) {
            boolean oceanNearby = isOcean(north) || isOcean(east) || isOcean(south) || isOcean(west);
            boolean mountainsNearby = isMountains(north) || isMountains(east)
                    || isMountains(south) || isMountains(west);
            boolean lowNearby = isLow(north) || isLow(east) || isLow(south) || isLow(west);

            if (isLow(center) && oceanNearby && mountainsNearby) {
                return TerrainProfile.COASTAL_MOUNTAINS;
            }
            if (center == TerrainProfile.HIGHLANDS && lowNearby) {
                return TerrainProfile.ROLLING_HILLS;
            }
            if (isMountains(center) && lowNearby) {
                return TerrainProfile.ROLLING_HILLS;
            }
            if (isLow(center) && mountainsNearby) {
                return TerrainProfile.ROLLING_HILLS;
            }
            if (center == TerrainProfile.DEEP_OCEAN
                    && (!isOcean(north) || !isOcean(east) || !isOcean(south) || !isOcean(west))) {
                return TerrainProfile.OCEAN;
            }
            return center;
        }

        private static boolean isOcean(TerrainProfile profile) {
            return profile == TerrainProfile.DEEP_OCEAN || profile == TerrainProfile.OCEAN;
        }

        private static boolean isMountains(TerrainProfile profile) {
            return profile == TerrainProfile.MOUNTAINS || profile == TerrainProfile.COASTAL_MOUNTAINS;
        }

        private static boolean isLow(TerrainProfile profile) {
            return profile == TerrainProfile.PLAINS
                    || profile == TerrainProfile.HILLS
                    || profile == TerrainProfile.ISLAND;
        }

        private static int chooseNormal(long seed, int x, int z, int first, int second, int third, int fourth) {
            if (first == second) {
                return first == third || third != fourth ? first : choose(seed, x, z, first, third);
            } else if (first == third) {
                return second != fourth ? first : choose(seed, x, z, first, second);
            } else if (first == fourth) {
                return second != third ? first : choose(seed, x, z, first, second);
            } else if (second == third || second == fourth) {
                return second;
            } else if (third == fourth) {
                return third;
            }
            return choose(seed, x, z, first, second, third, fourth);
        }

        private static int choose(long seed, int x, int z, int first, int second) {
            return random(seed, x, z, 2) == 0 ? first : second;
        }

        private static int choose(long seed, int x, int z, int first, int second, int third, int fourth) {
            return switch (random(seed, x, z, 4)) {
                case 0 -> first;
                case 1 -> second;
                case 2 -> third;
                default -> fourth;
            };
        }

        private static int random(long seed, int x, int z, int bound) {
            long areaSeed = murmurHash3(seed);
            long coordinateSeed = (((x * 501125321L) ^ (z * 1136930381L) ^ areaSeed) * 0x27D4EB2DL);
            return new XoroshiroRandomSource(coordinateSeed).nextInt(bound);
        }

        private static long murmurHash3(long value) {
            value ^= value >>> 33;
            value *= 0xFF51AFD7ED558CCDL;
            value ^= value >>> 33;
            value *= 0xC4CEB9FE1A85EC53L;
            return value ^ (value >>> 33);
        }

        private static final class LayerCaches {
            private final IntCache edge = new IntCache(CACHE_SIZE);
            private final IntCache[] zooms = new IntCache[ZOOM_COUNT];
            private final IntCache shore = new IntCache(CACHE_SIZE);
            private final IntCache moreShore = new IntCache(CACHE_SIZE);
            private final IntCache smooth = new IntCache(CACHE_SIZE);
            private final IntCache cleaned = new IntCache(CACHE_SIZE);

            private LayerCaches() {
                for (int stage = 0; stage < zooms.length; stage++) {
                    zooms[stage] = new IntCache(CACHE_SIZE);
                }
            }
        }

        @FunctionalInterface
        private interface IntComputer {
            int compute(int x, int z);
        }

        /** Per-thread direct cache, matching TFC's cheap Area cache semantics. */
        private static final class IntCache {
            private final long[] keys;
            private final int[] values;
            private final boolean[] valid;
            private final int mask;

            private IntCache(int size) {
                keys = new long[size];
                values = new int[size];
                valid = new boolean[size];
                mask = size - 1;
            }

            private int getOrCompute(int x, int z, IntComputer computer) {
                long key = ChunkPos.asLong(x, z);
                int index = mix(key) & mask;
                if (valid[index] && keys[index] == key) {
                    return values[index];
                }
                int value = computer.compute(x, z);
                keys[index] = key;
                values[index] = value;
                valid[index] = true;
                return value;
            }

            private void put(int x, int z, int value) {
                long key = ChunkPos.asLong(x, z);
                int index = mix(key) & mask;
                keys[index] = key;
                values[index] = value;
                valid[index] = true;
            }

            private static int mix(long value) {
                value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
                value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
                value ^= value >>> 31;
                return (int) (value ^ (value >>> 32));
            }
        }
    }
}
