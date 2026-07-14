package gregtech.worldgen.surface;

import gregapi.data.DimensionList;
import gregtech.registry.GTBlocks;
import gregtech.block.SurfaceLooseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Generates loose pickable surface objects: sticks and rock chips.
 *
 * <p>These are intentionally block-based, not entity-based, so they are stable
 * worldgen decorations and do not create entity ticking cost.</p>
 */
public final class SurfaceLooseObjectWorldgen {
    private static final int STICK_ATTEMPTS_PER_CHUNK = 4;
    private static final int ROCK_ATTEMPTS_PER_CHUNK = 3;
    private static final int STICK_CHANCE = 1;
    private static final int ROCK_CHANCE = 1;
    private static final int LOCAL_SEARCH_RADIUS = 5;

    private SurfaceLooseObjectWorldgen() {
    }

    public static int generate(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        if (!DimensionList.isEarthLike(level)) {
            return 0;
        }

        int generated = 0;
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        long seed = level.getSeed();

        for (int attempt = 0; attempt < STICK_ATTEMPTS_PER_CHUNK; attempt++) {
            if (Math.floorMod(stableHash(seed ^ 0x5711_CCEEL, chunkX, chunkZ, attempt), STICK_CHANCE) == 0) {
                generated += tryPlace(level, chunk, pos, seed ^ 0x57AC_1E57L, chunkX, chunkZ, attempt, GTBlocks.SURFACE_STICK.get().defaultBlockState());
            }
        }

        for (int attempt = 0; attempt < ROCK_ATTEMPTS_PER_CHUNK; attempt++) {
            if (Math.floorMod(stableHash(seed ^ 0xA0CC_51EEL, chunkX, chunkZ, attempt), ROCK_CHANCE) == 0) {
                generated += tryPlace(level, chunk, pos, seed ^ 0x20CC_1E57L, chunkX, chunkZ, attempt, GTBlocks.SURFACE_ROCK.get().defaultBlockState());
            }
        }

        return generated;
    }

    private static int tryPlace(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            long seed,
            int chunkX,
            int chunkZ,
            int attempt,
            BlockState looseState
    ) {
        int startLocalX = Math.floorMod(stableHash(seed, chunkX, chunkZ, attempt), 16);
        int startLocalZ = Math.floorMod(stableHash(seed ^ 0x9E37_79B9L, chunkX, chunkZ, attempt), 16);
        SurfacePlacement placement = findNearbySurfacePlacement(level, chunk, pos, startLocalX, startLocalZ);

        if (placement == null) {
            return 0;
        }

        pos.set(placement.x(), placement.y(), placement.z());
        chunk.setBlockState(pos, looseState, false);
        return 1;
    }

    private static SurfacePlacement findNearbySurfacePlacement(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int startLocalX,
            int startLocalZ
    ) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int radius = 0; radius <= LOCAL_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    int localX = startLocalX + dx;
                    int localZ = startLocalZ + dz;
                    if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
                        continue;
                    }

                    int x = minX + localX;
                    int z = minZ + localZ;
                    int y = findSurfacePlacementY(level, chunk, pos, x, z);
                    if (y == Integer.MIN_VALUE) {
                        continue;
                    }

                    pos.set(x, y, z);
                    if (level.canSeeSky(pos)) {
                        return new SurfacePlacement(x, y, z);
                    }
                }
            }
        }

        return null;
    }

    private static int findSurfacePlacementY(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int x,
            int z
    ) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 1;

        for (int y = maxY; y >= minY; y--) {
            pos.set(x, y, z);
            BlockState currentState = chunk.getBlockState(pos);

            if (canReplaceAtSurface(currentState)) {
                BlockPos belowPos = pos.below();
                BlockState belowState = chunk.getBlockState(belowPos);

                if (canPlaceOn(level, belowPos, belowState)) {
                    return y;
                }

                continue;
            }

            if (!canPlaceOn(level, pos, currentState)) {
                continue;
            }

            int aboveY = y + 1;
            if (aboveY >= level.getMaxBuildHeight()) {
                return Integer.MIN_VALUE;
            }

            pos.set(x, aboveY, z);
            if (canReplaceAtSurface(chunk.getBlockState(pos))) {
                return aboveY;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean canReplaceAtSurface(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(BlockTags.FLOWERS);
    }

    private static boolean canPlaceOn(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (!SurfaceLooseBlock.isValidSupportBlock(state)) {
            return false;
        }

        if (level.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
            return false;
        }

        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static int stableHash(long seed, int chunkX, int chunkZ, int salt) {
        long value = seed;
        value ^= chunkX * 0x9E37_79B9_7F4A_7C15L;
        value ^= chunkZ * 0xC2B2_AE3D_27D4_EB4FL;
        value ^= salt * 0x1656_67B1L;
        value ^= value >>> 33;
        value *= 0xFF51_AFD7_ED55_8CCDL;
        value ^= value >>> 33;
        value *= 0xC4CE_B9FE_1A85_EC53L;
        value ^= value >>> 33;

        return (int) value;
    }

    private record SurfacePlacement(int x, int y, int z) {
    }
}
