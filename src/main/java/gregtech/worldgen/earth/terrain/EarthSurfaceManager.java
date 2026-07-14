/*
 * Surface construction is kept separate from density following
 * TerraFirmaCraft/TerraFirmaGreg's world-generation architecture.
 */
package gregtech.worldgen.earth.terrain;

import gregtech.worldgen.earth.climate.EarthBiomeSemantics;
import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import gregtech.GT6UOU;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Applies a thin material skin without changing the prepared terrain height. */
public final class EarthSurfaceManager {
    private static final TagKey<net.minecraft.world.level.block.Block> CAVE_REPLACEABLES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, "earth_carver_replaceables")
    );
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState MUD = Blocks.MUD.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();

    private final EarthGeneratorState state;
    private final SimplexNoise caveFloorNoise;

    public EarthSurfaceManager(EarthGeneratorState state) {
        this.state = state;
        this.caveFloorNoise = new SimplexNoise(new XoroshiroRandomSource(
                state.terrainSeed() ^ 0x43415645464C4F52L
        ));
    }

    public void buildSurface(ChunkAccess chunk, EarthNoiseFiller.ChunkTerrain terrain) {
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int surfaceY = terrain.surfaceY(localX, localZ);
                TerrainProfile profile = terrain.profile(localX, localZ);
                float temperature = terrain.temperature(localX, localZ);
                float rainfall = terrain.rainfall(localX, localZ);

                if (profile == TerrainProfile.LAKE) {
                    replaceTop(chunk, terrain, localX, localZ, surfaceY, GRAVEL, GRAVEL, 3);
                } else if (terrain.river(localX, localZ)) {
                    replaceTop(chunk, terrain, localX, localZ, surfaceY, GRAVEL, GRAVEL, 3);
                } else if (isSandyShore(profile)
                        && surfaceY >= EarthTerrainProfiles.SEA_LEVEL - 4) {
                    replaceTop(chunk, terrain, localX, localZ, surfaceY, SAND, SAND, 4);
                } else if (!isMarineOrRockyShore(profile)) {
                    if (EarthBiomeSemantics.temperatureAtFreezing(temperature, rainfall)) {
                        replaceTop(chunk, terrain, localX, localZ, surfaceY, GRASS, DIRT, 4);
                        if (surfaceY + 1 < terrain.maxY()) {
                            setBlock(chunk, localX, surfaceY + 1, localZ, SNOW);
                        }
                    } else if (rainfall < 60.0F) {
                        replaceTop(chunk, terrain, localX, localZ, surfaceY, SAND, SAND, 4);
                    } else if (rainfall < 155.0F) {
                        replaceTop(chunk, terrain, localX, localZ, surfaceY, COARSE_DIRT, DIRT, 4);
                    } else if (rainfall > 425.0F && temperature > 12.0F && surfaceY < 76) {
                        replaceTop(chunk, terrain, localX, localZ, surfaceY, MUD, DIRT, 4);
                    } else {
                        replaceTop(chunk, terrain, localX, localZ, surfaceY, GRASS, DIRT, 4);
                    }
                }

                int fluidY = terrain.fluidY(localX, localZ);
                if (fluidY != Integer.MIN_VALUE
                        && EarthBiomeSemantics.temperatureAtFreezing(temperature, rainfall)) {
                    setBlock(chunk, localX, fluidY, localZ, ICE);
                }
            }
        }
    }

    /**
     * Applies the climate/karst cave-surface pass after all Earth carvers.
     * Ores are intentionally not in the replaceable tag and remain untouched.
     */
    public void buildCaveSurfaces(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < 16; localZ++) {
            int z = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int x = minX + localX;
                float rainfall = state.climateModel().averageRainfall(x, z);
                boolean karst = state.karstModel().isKarst(x, z);
                int surfaceY = state.heightFiller().sampleBlockHeight(x, z);
                for (int y = chunk.getMinBuildHeight() + 6; y < surfaceY - 2; y++) {
                    cursor.set(x, y, z);
                    BlockState empty = chunk.getBlockState(cursor);
                    if (!empty.isAir() && !empty.is(Blocks.WATER)) continue;

                    below.set(x, y - 1, z);
                    BlockState floor = chunk.getBlockState(below);
                    if (floor.is(CAVE_REPLACEABLES)) {
                        /*
                         * Host rock remains the normal cave floor. The skewed
                         * 2D slice turns one simplex field into coherent 3D
                         * patches without painting every exposed floor block.
                         */
                        double patch = caveFloorNoise.getValue(
                                (x + y * 0.37D) * 0.045D,
                                (z - y * 0.29D) * 0.045D
                        );
                        BlockState replacement = null;
                        if (rainfall > 425.0F && patch > 0.30D) {
                            replacement = MUD;
                        } else if (rainfall > 300.0F && patch > 0.42D) {
                            replacement = Blocks.MOSS_BLOCK.defaultBlockState();
                        } else if (patch > 0.58D) {
                            replacement = GRAVEL;
                        }
                        if (replacement != null) {
                            chunk.setBlockState(below, replacement, false);
                        }
                    }

                    if (karst && rainfall > 250.0F && Math.floorMod(hash(x, y, z), 113) == 0) {
                        above.set(x, y + 1, z);
                        if (chunk.getBlockState(above).is(CAVE_REPLACEABLES)) {
                            chunk.setBlockState(cursor, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                    .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN), false);
                        } else if (floor.is(CAVE_REPLACEABLES)) {
                            chunk.setBlockState(cursor, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                    .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP), false);
                        }
                    }
                }
            }
        }
    }

    /**
     * Restores the continuous source-water ceiling after cave/canyon carving.
     * Carvers may flood an opening into the seabed, but they may never leave a
     * one-block air lens at or below the generated ocean surface.
     */
    public void repairOceanWaterSurface(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < 16; localZ++) {
            int z = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int x = minX + localX;
                TerrainProfile profile = state.terrainProfiles().profileAtBlock(x, z);
                if (profile != TerrainProfile.OCEAN && profile != TerrainProfile.DEEP_OCEAN) continue;

                int fromY = Math.max(chunk.getMinBuildHeight(), EarthTerrainProfiles.SEA_LEVEL - 2);
                for (int y = fromY; y <= EarthTerrainProfiles.SEA_LEVEL; y++) {
                    cursor.set(x, y, z);
                    if (chunk.getBlockState(cursor).isAir()) {
                        chunk.setBlockState(cursor, Blocks.WATER.defaultBlockState(), false);
                        chunk.markPosForPostprocessing(cursor);
                    }
                }
            }
        }
    }

    private static int hash(int x, int y, int z) {
        long value = x * 0x9E3779B97F4A7C15L ^ y * 0x165667B19E3779F9L ^ z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return (int) (value ^ value >>> 31);
    }

    private static boolean isSandyShore(TerrainProfile profile) {
        return profile == TerrainProfile.SHORE
                || profile == TerrainProfile.TIDAL_FLATS
                || profile == TerrainProfile.COASTAL_DUNES
                || profile == TerrainProfile.EMBAYMENTS;
    }

    /** Deep ocean and rocky coast keep the sampled GT6UOU host rock exposed. */
    private static boolean isMarineOrRockyShore(TerrainProfile profile) {
        return profile == TerrainProfile.DEEP_OCEAN
                || profile == TerrainProfile.OCEAN
                || profile == TerrainProfile.ROCKY_SHORES
                || EarthShoreProfiles.isShore(profile);
    }

    private static void replaceTop(
            ChunkAccess chunk,
            EarthNoiseFiller.ChunkTerrain terrain,
            int localX,
            int localZ,
            int surfaceY,
            BlockState top,
            BlockState under,
            int depth
    ) {
        int bottomY = Math.max(terrain.minY() + 5, surfaceY - depth + 1);
        for (int y = surfaceY; y >= bottomY; y--) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            section.setBlockState(localX, y & 15, localZ, y == surfaceY ? top : under, false);
        }
    }

    private static void setBlock(
            ChunkAccess chunk,
            int localX,
            int y,
            int localZ,
            BlockState state
    ) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(localX, y & 15, localZ, state, false);
    }
}
