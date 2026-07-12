package gregtech.worldgen;

import gregtech.registry.GTFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * GTM-like non-bedrock raw oil sprout generation.
 *
 * <p>Rules mirror GregTech Modern's raw_oil_sprout in spirit: rarity 1/64,
 * Y 10-40, underground oil pocket size 13-15, and 40% chance to grow a vertical
 * sprout toward the surface. The implementation writes only the currently
 * decorated chunk slice, so it stays safe inside the chunk-generation hook.</p>
 */
public final class RawOilSproutWorldgen {
    private static final int CHANCE = 64;
    private static final int MIN_Y = 10;
    private static final int MAX_Y = 40;
    private static final int MIN_SIZE = 13;
    private static final int MAX_SIZE = 15;
    private static final int MIN_SURFACE_OFFSET = 6;
    private static final int MAX_SURFACE_OFFSET = 9;
    private static final int SPROUT_CHANCE_PERCENT = 40;
    private static final int SEARCH_DISTANCE_CHUNKS = 1;
    private static final long SEED_SALT = 0x5241574F494C3031L; // "RAWOIL01"

    private RawOilSproutWorldgen() {
    }

    public static int generate(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        if (!GTDimensions.isEarthLike(level)) {
            return 0;
        }

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        long seed = level.getSeed() ^ SEED_SALT;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockState rawOilState = GTFluids.RAW_OIL.get().defaultFluidState().createLegacyBlock();

        int placed = 0;
        for (int originChunkX = chunkX - SEARCH_DISTANCE_CHUNKS; originChunkX <= chunkX + SEARCH_DISTANCE_CHUNKS; originChunkX++) {
            for (int originChunkZ = chunkZ - SEARCH_DISTANCE_CHUNKS; originChunkZ <= chunkZ + SEARCH_DISTANCE_CHUNKS; originChunkZ++) {
                placed += generateOriginSlice(level, chunk, pos, minX, minZ, seed, originChunkX, originChunkZ, rawOilState);
            }
        }

        return placed;
    }

    private static int generateOriginSlice(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int currentChunkMinX,
            int currentChunkMinZ,
            long seed,
            int originChunkX,
            int originChunkZ,
            BlockState rawOilState
    ) {
        if (Math.floorMod(stableChunkHash(seed, originChunkX, originChunkZ), CHANCE) != 0) {
            return 0;
        }

        int originMinX = originChunkX << 4;
        int originMinZ = originChunkZ << 4;
        int centerX = originMinX + randomBetween(seed ^ 0x9E3779B97F4A7C15L, originChunkX, originChunkZ, 0, 15);
        int centerZ = originMinZ + randomBetween(seed ^ 0xC2B2AE3D27D4EB4FL, originChunkX, originChunkZ, 0, 15);
        int centerY = randomBetween(seed ^ 0x165667B19E3779F9L, originChunkX, originChunkZ, MIN_Y, MAX_Y);
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);

        if (centerY >= surfaceY || level.isOutsideBuildHeight(centerY)) {
            return 0;
        }

        int size = randomBetween(seed ^ 0x85EBCA77C2B2AE63L, originChunkX, originChunkZ, MIN_SIZE, MAX_SIZE);
        int placed = placePocket(chunk, pos, currentChunkMinX, currentChunkMinZ, centerX, centerY, centerZ, size, rawOilState);

        int sproutRoll = Math.floorMod(stableChunkHash(seed ^ 0x27D4EB2F165667C5L, originChunkX, originChunkZ), 100);
        if (sproutRoll < SPROUT_CHANCE_PERCENT) {
            int offset = randomBetween(seed ^ 0x94D049BB133111EBL, originChunkX, originChunkZ, MIN_SURFACE_OFFSET, MAX_SURFACE_OFFSET);
            placed += placeSprout(level, chunk, pos, centerX, centerY, centerZ, surfaceY, surfaceY + offset, rawOilState);
        }

        return placed;
    }

    private static int placePocket(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int centerX,
            int centerY,
            int centerZ,
            int size,
            BlockState rawOilState
    ) {
        int placed = 0;
        int radius = Math.ceilDiv(size, 2);
        int x0 = centerX - radius;
        int y0 = centerY - radius;
        int z0 = centerZ - radius;
        int width = size + 1;
        int height = size + 1;
        int length = size + 1;

        for (int x = 0; x < width; x++) {
            float dx = x * 2.0F / width - 1.0F;
            if (dx * dx > 1.0F) {
                continue;
            }

            for (int y = 0; y < height; y++) {
                int currentY = y0 + y;
                float dy = y * 2.0F / height - 1.0F;
                if (dx * dx + dy * dy > 1.0F || chunk.isOutsideBuildHeight(currentY)) {
                    continue;
                }

                for (int z = 0; z < length; z++) {
                    float dz = z * 2.0F / length - 1.0F;
                    if (dx * dx + dy * dy + dz * dz > 1.0F) {
                        continue;
                    }

                    int currentX = x0 + x;
                    int currentZ = z0 + z;
                    if (currentX < minX || currentX >= minX + 16 || currentZ < minZ || currentZ >= minZ + 16) {
                        continue;
                    }

                    placed += setRawOil(chunk, pos, currentX, currentY, currentZ, rawOilState);
                }
            }
        }

        return placed;
    }

    private static int placeSprout(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int centerX,
            int centerY,
            int centerZ,
            int surfaceY,
            int maxY,
            BlockState rawOilState
    ) {
        int placed = 0;
        int cappedMaxY = Math.min(maxY, level.getMaxBuildHeight() - 1);

        for (int y = centerY; y <= cappedMaxY; y++) {
            placed += setRawOil(chunk, pos, centerX, y, centerZ, rawOilState);

            if (y <= surfaceY) {
                placed += setRawOil(chunk, pos, centerX + 1, y, centerZ, rawOilState);
                placed += setRawOil(chunk, pos, centerX - 1, y, centerZ, rawOilState);
                placed += setRawOil(chunk, pos, centerX, y, centerZ + 1, rawOilState);
                placed += setRawOil(chunk, pos, centerX, y, centerZ - 1, rawOilState);
            }
        }

        return placed;
    }

    private static int setRawOil(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int x,
            int y,
            int z,
            BlockState rawOilState
    ) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
            return 0;
        }

        if (chunk.isOutsideBuildHeight(y)) {
            return 0;
        }

        pos.set(x, y, z);
        BlockState currentState = chunk.getBlockState(pos);
        if (currentState.is(Blocks.BEDROCK) || currentState.is(rawOilState.getBlock())) {
            return 0;
        }

        chunk.setBlockState(pos, rawOilState, false);
        chunk.markPosForPostprocessing(pos);
        return 1;
    }

    private static int randomBetween(long seed, int chunkX, int chunkZ, int minInclusive, int maxInclusive) {
        int bound = maxInclusive - minInclusive + 1;
        return minInclusive + Math.floorMod(stableChunkHash(seed, chunkX, chunkZ), bound);
    }

    private static int stableChunkHash(long seed, int chunkX, int chunkZ) {
        long value = seed;
        value ^= chunkX * 0x9E37_79B9_7F4A_7C15L;
        value ^= chunkZ * 0xC2B2_AE3D_27D4_EB4FL;
        value ^= value >>> 33;
        value *= 0xFF51_AFD7_ED55_8CCDL;
        value ^= value >>> 33;
        value *= 0xC4CE_B9FE_1A85_EC53L;
        value ^= value >>> 33;

        return (int) value;
    }
}
