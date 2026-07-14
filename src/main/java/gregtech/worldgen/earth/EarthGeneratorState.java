package gregtech.worldgen.earth;

import gregtech.GT6UOU;
import gregtech.worldgen.earth.region.EarthRegionGenerator;
import gregtech.worldgen.earth.region.EarthUnits;
import gregtech.worldgen.earth.river.EarthRiverSampler;
import gregtech.worldgen.earth.terrain.EarthHeightFiller;
import gregtech.worldgen.earth.terrain.EarthKarstModel;
import gregtech.worldgen.earth.terrain.EarthNoiseFiller;
import gregtech.worldgen.earth.terrain.EarthShoreProfiles;
import gregtech.worldgen.earth.terrain.EarthSurfaceManager;
import gregtech.worldgen.earth.terrain.EarthTerrainProfiles;
import gregtech.worldgen.earth.terrain.EarthUndergroundSystem;
import gregtech.worldgen.geology.StoneLayerStackSampler;
import gregtech.worldgen.earth.climate.EarthClimateModel;
import gregtech.worldgen.earth.climate.EarthSeasonalClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Seed-derived state owned by one loaded Earth level.
 *
 * <p>The state is initialized once from {@link net.minecraft.server.level.ServerLevel#getSeed()}
 * and is shared with Minecraft's RandomState. Region data is therefore identical
 * for runtime terrain, structures and previews.</p>
 */
public final class EarthGeneratorState {
    private static final long REGION_SALT = 0x2D0F28C7E7E786A3L;
    private static final long TERRAIN_SALT = 0x7A62D5D17E430D0BL;
    private static final long RIVER_SALT = 0x1562A9F04C34D19DL;

    private final ResourceKey<Level> dimension;
    private final long worldSeed;
    private final long regionSeed;
    private final long terrainSeed;
    private final long riverSeed;
    private final long fingerprint;
    private final EarthRegionGenerator regionGenerator;
    private final EarthClimateModel climateModel;
    private final EarthSeasonalClimate seasonalClimate;
    private final EarthTerrainProfiles terrainProfiles;
    private final EarthShoreProfiles shoreProfiles;
    private final EarthRiverSampler riverSampler;
    private final EarthHeightFiller heightFiller;
    private final StoneLayerStackSampler stoneLayerSampler;
    private final EarthKarstModel karstModel;
    private final EarthNoiseFiller noiseFiller;
    private final EarthSurfaceManager surfaceManager;
    private volatile EarthUndergroundSystem undergroundSystem;
    private volatile BlockPos nearestContinentalSpawn;

    private EarthGeneratorState(
            ResourceKey<Level> dimension,
            long worldSeed,
            long regionSeed,
            long terrainSeed,
            long riverSeed,
            long fingerprint
    ) {
        this.dimension = dimension;
        this.worldSeed = worldSeed;
        this.regionSeed = regionSeed;
        this.terrainSeed = terrainSeed;
        this.riverSeed = riverSeed;
        this.fingerprint = fingerprint;
        this.regionGenerator = new EarthRegionGenerator(regionSeed);
        this.climateModel = new EarthClimateModel(regionGenerator);
        this.seasonalClimate = new EarthSeasonalClimate(regionSeed, climateModel);
        this.terrainProfiles = new EarthTerrainProfiles(regionGenerator, terrainSeed);
        this.shoreProfiles = new EarthShoreProfiles(terrainSeed ^ 0x3C79AC492BA7B653L);
        this.riverSampler = new EarthRiverSampler(regionGenerator, riverSeed);
        this.stoneLayerSampler = new StoneLayerStackSampler(worldSeed, (blockX, blockZ) ->
                regionGenerator.getOrCreatePoint(
                        EarthUnits.blockToGrid(blockX),
                        EarthUnits.blockToGrid(blockZ)
                ).geologicalProvince()
        );
        this.karstModel = new EarthKarstModel(terrainSeed, stoneLayerSampler, climateModel, terrainProfiles);
        this.heightFiller = new EarthHeightFiller(terrainProfiles, shoreProfiles, riverSampler, karstModel);
        this.noiseFiller = new EarthNoiseFiller(this, stoneLayerSampler);
        this.surfaceManager = new EarthSurfaceManager(this);
    }

    public static EarthGeneratorState create(ResourceKey<Level> dimension, long worldSeed) {
        long dimensionSalt = mix64(dimension.location().toString().hashCode());
        long base = mix64(worldSeed ^ dimensionSalt);
        long regionSeed = mix64(base ^ REGION_SALT);
        long terrainSeed = mix64(base ^ TERRAIN_SALT);
        long riverSeed = mix64(base ^ RIVER_SALT);
        long fingerprint = mix64(regionSeed ^ Long.rotateLeft(terrainSeed, 21) ^ Long.rotateLeft(riverSeed, 42));
        return new EarthGeneratorState(dimension, worldSeed, regionSeed, terrainSeed, riverSeed, fingerprint);
    }

    public boolean matches(ResourceKey<Level> otherDimension, long otherSeed) {
        return dimension.equals(otherDimension) && worldSeed == otherSeed;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long worldSeed() {
        return worldSeed;
    }

    public long regionSeed() {
        return regionSeed;
    }

    public long terrainSeed() {
        return terrainSeed;
    }

    public long riverSeed() {
        return riverSeed;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public EarthRegionGenerator regionGenerator() {
        return regionGenerator;
    }

    public EarthClimateModel climateModel() {
        return climateModel;
    }

    public EarthSeasonalClimate seasonalClimate() {
        return seasonalClimate;
    }

    public EarthTerrainProfiles terrainProfiles() {
        return terrainProfiles;
    }

    public EarthShoreProfiles shoreProfiles() {
        return shoreProfiles;
    }

    public EarthRiverSampler riverSampler() {
        return riverSampler;
    }

    public EarthHeightFiller heightFiller() {
        return heightFiller;
    }

    public StoneLayerStackSampler stoneLayerSampler() {
        return stoneLayerSampler;
    }

    public EarthNoiseFiller noiseFiller() {
        return noiseFiller;
    }

    public EarthSurfaceManager surfaceManager() {
        return surfaceManager;
    }

    /**
     * Finds and caches a safe point on the nearest continental land profile.
     * The search reads the deterministic terrain fields directly and therefore
     * does not generate thousands of ocean chunks while locating world spawn.
     */
    public BlockPos nearestContinentalSpawn() {
        BlockPos current = nearestContinentalSpawn;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = nearestContinentalSpawn;
            if (current == null) {
                current = locateNearestContinentalSpawn();
                nearestContinentalSpawn = current;
            }
        }
        return current;
    }

    private BlockPos locateNearestContinentalSpawn() {
        final int coarseStep = 256;
        final int maxRing = 256;
        SpawnCandidate coarse = null;

        for (int ring = 0; ring <= maxRing && coarse == null; ring++) {
            if (ring == 0) {
                coarse = continentalCandidate(0, 0, false);
                continue;
            }
            int edge = ring * coarseStep;
            for (int offset = -edge; offset <= edge; offset += coarseStep) {
                coarse = nearer(coarse, continentalCandidate(offset, -edge, false));
                coarse = nearer(coarse, continentalCandidate(offset, edge, false));
                coarse = nearer(coarse, continentalCandidate(-edge, offset, false));
                coarse = nearer(coarse, continentalCandidate(edge, offset, false));
            }
        }

        if (coarse == null) {
            GT6UOU.LOGGER.warn("No continental GT Earth spawn found within 65536 blocks; accepting nearest non-ocean land");
            for (int ring = 0; ring <= maxRing && coarse == null; ring++) {
                int edge = ring * coarseStep;
                for (int offset = -edge; offset <= edge; offset += coarseStep) {
                    coarse = nearer(coarse, continentalCandidate(offset, -edge, true));
                    coarse = nearer(coarse, continentalCandidate(offset, edge, true));
                    coarse = nearer(coarse, continentalCandidate(-edge, offset, true));
                    coarse = nearer(coarse, continentalCandidate(edge, offset, true));
                }
            }
        }

        if (coarse == null) {
            int y = Math.max(EarthTerrainProfiles.SEA_LEVEL + 2, heightFiller.sampleBlockHeight(0, 0) + 2);
            return new BlockPos(0, y, 0);
        }

        SpawnCandidate refined = null;
        for (int x = coarse.x - coarseStep; x <= coarse.x + coarseStep; x += 8) {
            for (int z = coarse.z - coarseStep; z <= coarse.z + coarseStep; z += 8) {
                SpawnCandidate candidate = continentalCandidate(x, z, false);
                if (candidate != null && locallySafe(candidate.x, candidate.z, candidate.surfaceY)) {
                    refined = nearer(refined, candidate);
                }
            }
        }
        if (refined == null) {
            refined = coarse;
        }

        BlockPos result = new BlockPos(refined.x, refined.surfaceY + 2, refined.z);
        GT6UOU.LOGGER.info("Selected GT Earth continental spawn at {} ({} blocks from origin)",
                result, Math.round(Math.sqrt(refined.distanceSquared)));
        return result;
    }

    private SpawnCandidate continentalCandidate(int x, int z, boolean allowIsland) {
        var profile = terrainProfiles.profileAtBlock(x, z);
        boolean continental = switch (profile) {
            case PLAINS, HILLS, ROLLING_HILLS, HIGHLANDS, MOUNTAINS -> true;
            case ISLAND -> allowIsland;
            default -> false;
        };
        if (!continental) {
            return null;
        }
        int surfaceY = heightFiller.sampleBlockHeight(x, z);
        if (surfaceY < EarthTerrainProfiles.SEA_LEVEL + 2) {
            return null;
        }
        return new SpawnCandidate(x, z, surfaceY, (long) x * x + (long) z * z);
    }

    private boolean locallySafe(int x, int z, int centerY) {
        int maximumDelta = 0;
        for (int dx = -8; dx <= 8; dx += 8) {
            for (int dz = -8; dz <= 8; dz += 8) {
                maximumDelta = Math.max(maximumDelta,
                        Math.abs(heightFiller.sampleBlockHeight(x + dx, z + dz) - centerY));
            }
        }
        return maximumDelta <= 4;
    }

    private static SpawnCandidate nearer(SpawnCandidate left, SpawnCandidate right) {
        if (right == null) return left;
        return left == null || right.distanceSquared < left.distanceSquared ? right : left;
    }

    private record SpawnCandidate(int x, int z, int surfaceY, long distanceSquared) {
    }

    /** Karst eligibility follows the actual top rock of our geological stack. */
    public boolean isKarstAt(int blockX, int blockZ) {
        return karstModel.isKarst(blockX, blockZ);
    }

    public EarthKarstModel karstModel() {
        return karstModel;
    }

    /**
     * Wires Minecraft's seed-specific noise registry exactly once for this
     * loaded Earth level. NormalNoise instances are immutable and safe to use
     * from concurrent chunk-generation workers.
     */
    public EarthUndergroundSystem undergroundSystem(RandomState randomState) {
        EarthUndergroundSystem current = undergroundSystem;
        if (current == null) {
            synchronized (this) {
                current = undergroundSystem;
                if (current == null) {
                    current = new EarthUndergroundSystem(this, randomState);
                    undergroundSystem = current;
                }
            }
        }
        return current;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
