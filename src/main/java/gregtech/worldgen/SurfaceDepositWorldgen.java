package gregtech.worldgen;

import gregapi.code.BlockTypeDefinitions;
import gregapi.data.RocksList;
import gregtech.registry.GTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Set;

/**
 * Generates surface and near-surface loose deposits: vanilla sand replacement,
 * black sand patches, clay pits and turf.
 */
public final class SurfaceDepositWorldgen {
    private static final int CLAY_PIT_CHANCE = 320;
    private static final int CLAY_PIT_MAX_DEPTH_PER_COLUMN = 7;
    private static final int CLAY_PIT_RADIUS = 24;
    private static final int CLAY_PIT_SEARCH_DISTANCE_CHUNKS = Math.ceilDiv(CLAY_PIT_RADIUS, 16);

    private static final int TURF_CHANCE = 32;
    private static final int TURF_MAX_DEPTH_PER_COLUMN = 2;
    private static final int TURF_RADIUS = 24;
    private static final int TURF_SEARCH_DISTANCE_CHUNKS = Math.ceilDiv(TURF_RADIUS, 16);

    private static final int BLACK_SAND_GRID_SIZE_CHUNKS = 3;
    private static final int BLACK_SAND_RANDOM_OFFSET_BLOCKS = 12;
    private static final int BLACK_SAND_CHANCE = 64;
    private static final int BLACK_SAND_MAX_DEPTH_PER_COLUMN = 2;
    private static final int BLACK_SAND_PATCH_RADIUS_X = 24;
    private static final int BLACK_SAND_PATCH_RADIUS_Z = 20;
    private static final int BLACK_SAND_SEARCH_DISTANCE_CHUNKS = Math.ceilDiv(
            Math.max(BLACK_SAND_PATCH_RADIUS_X, BLACK_SAND_PATCH_RADIUS_Z) + BLACK_SAND_RANDOM_OFFSET_BLOCKS,
            16
    );

    private static final Set<Block> BLACK_SAND_REPLACEABLE_BLOCKS = Set.of(
            Blocks.DIRT,
            Blocks.GRAVEL,
            Blocks.CLAY
    );

    private SurfaceDepositWorldgen() {
    }

    public static int generate(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        if (!GTDimensions.isEarthLike(level)) {
            return 0;
        }

        BlockState sandReplacementState = findSandBlock("sand").defaultBlockState();
        BlockState redSandReplacementState = findSandBlock("red_sand").defaultBlockState();
        BlockState turfState = findOtherBlock("turf").defaultBlockState();

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int generatedBlocks = 0;

        generatedBlocks += replaceVanillaSand(chunk, pos, minX, minZ, minY, maxY, sandReplacementState, redSandReplacementState);
        generatedBlocks += generateBlackSand(level, chunk, pos, minX, minZ, sandReplacementState, redSandReplacementState);
        generatedBlocks += generateClayPits(level, chunk, pos, minX, minZ, sandReplacementState, redSandReplacementState);
        generatedBlocks += generateTurf(level, chunk, pos, minX, minZ, turfState);

        return generatedBlocks;
    }

    private static int replaceVanillaSand(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int minY,
            int maxY,
            BlockState sandReplacementState,
            BlockState redSandReplacementState
    ) {
        int replacedBlocks = 0;

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;

            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;

                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    BlockState currentState = chunk.getBlockState(pos);
                    Block currentBlock = currentState.getBlock();

                    if (currentBlock == Blocks.SAND) {
                        chunk.setBlockState(pos, sandReplacementState, false);
                        replacedBlocks++;
                    } else if (currentBlock == Blocks.RED_SAND) {
                        chunk.setBlockState(pos, redSandReplacementState, false);
                        replacedBlocks++;
                    }
                }
            }
        }

        return replacedBlocks;
    }

    private static int generateClayPits(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            BlockState sandReplacementState,
            BlockState redSandReplacementState
    ) {
        int replacedBlocks = 0;
        int upperY = Math.min(level.getSeaLevel() + 16, level.getMaxBuildHeight() - 1);
        int lowerY = Math.max(level.getSeaLevel() - 8, level.getMinBuildHeight());
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int originChunkX = chunkX - CLAY_PIT_SEARCH_DISTANCE_CHUNKS; originChunkX <= chunkX + CLAY_PIT_SEARCH_DISTANCE_CHUNKS; originChunkX++) {
            for (int originChunkZ = chunkZ - CLAY_PIT_SEARCH_DISTANCE_CHUNKS; originChunkZ <= chunkZ + CLAY_PIT_SEARCH_DISTANCE_CHUNKS; originChunkZ++) {
                for (BlockTypeDefinitions.ClayType clay : RocksList.CLAYS) {
                    ClayPit pit = createClayPit(level, originChunkX, originChunkZ, clay);

                    if (pit == null || !pit.intersectsChunk(minX, minZ)) {
                        continue;
                    }

                    replacedBlocks += placeClayPitSlice(
                            level,
                            chunk,
                            pos,
                            minX,
                            minZ,
                            upperY,
                            lowerY,
                            pit,
                            sandReplacementState,
                            redSandReplacementState
                    );
                }
            }
        }

        return replacedBlocks;
    }

    private static int placeClayPitSlice(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int upperY,
            int lowerY,
            ClayPit pit,
            BlockState sandReplacementState,
            BlockState redSandReplacementState
    ) {
        int replacedBlocks = 0;

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;

            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;

                if (!pit.contains(x, z) || !isValidClayPitBiome(level, x, z)) {
                    continue;
                }

                int generatedInColumn = 0;

                for (int y = upperY; y >= lowerY && generatedInColumn < CLAY_PIT_MAX_DEPTH_PER_COLUMN; y--) {
                    pos.set(x, y, z);
                    BlockState currentState = chunk.getBlockState(pos);

                    if (canReplaceWithClayPit(currentState, generatedInColumn > 0, sandReplacementState, redSandReplacementState)) {
                        chunk.setBlockState(pos, pit.state(), false);
                        generatedInColumn++;
                        replacedBlocks++;
                    } else if (generatedInColumn > 0 && currentState.isSolid()) {
                        break;
                    }
                }
            }
        }

        return replacedBlocks;
    }

    private static ClayPit createClayPit(WorldGenLevel level, int originChunkX, int originChunkZ, BlockTypeDefinitions.ClayType clay) {
        long seed = level.getSeed() ^ clay.id().hashCode();

        if (Math.floorMod(stableChunkHash(seed, originChunkX, originChunkZ), CLAY_PIT_CHANCE) != 0) {
            return null;
        }

        int centerX = (originChunkX << 4) + 8;
        int centerZ = (originChunkZ << 4) + 8;

        return new ClayPit(centerX, centerZ, findClayBlock(clay.id()).defaultBlockState());
    }

    private static boolean isValidClayPitBiome(WorldGenLevel level, int x, int z) {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));

        return biome.is(Biomes.PLAINS)
                || biome.is(Biomes.SUNFLOWER_PLAINS)
                || biome.is(Biomes.SAVANNA)
                || biome.is(Biomes.SAVANNA_PLATEAU)
                || biome.is(Biomes.WINDSWEPT_SAVANNA);
    }

    private static boolean canReplaceWithClayPit(
            BlockState state,
            boolean alreadyGenerating,
            BlockState sandReplacementState,
            BlockState redSandReplacementState
    ) {
        Block block = state.getBlock();

        if (block == Blocks.DIRT || block == Blocks.SAND || block == Blocks.CLAY
                || state.is(sandReplacementState.getBlock()) || state.is(redSandReplacementState.getBlock())) {
            return true;
        }

        return alreadyGenerating && (block == Blocks.STONE || block == Blocks.GRAVEL);
    }

    private static int generateTurf(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            BlockState turfState
    ) {
        int replacedBlocks = 0;
        int upperY = Math.min(level.getSeaLevel() + 1, level.getMaxBuildHeight() - 1);
        int lowerY = Math.max(level.getSeaLevel() - 12, level.getMinBuildHeight());
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int originChunkX = chunkX - TURF_SEARCH_DISTANCE_CHUNKS; originChunkX <= chunkX + TURF_SEARCH_DISTANCE_CHUNKS; originChunkX++) {
            for (int originChunkZ = chunkZ - TURF_SEARCH_DISTANCE_CHUNKS; originChunkZ <= chunkZ + TURF_SEARCH_DISTANCE_CHUNKS; originChunkZ++) {
                TurfPatch patch = createTurfPatch(level, originChunkX, originChunkZ, turfState);

                if (patch == null || !patch.intersectsChunk(minX, minZ)) {
                    continue;
                }

                replacedBlocks += placeTurfPatchSlice(level, chunk, pos, minX, minZ, upperY, lowerY, patch);
            }
        }

        return replacedBlocks;
    }

    private static int placeTurfPatchSlice(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int upperY,
            int lowerY,
            TurfPatch patch
    ) {
        int replacedBlocks = 0;

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;

            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;

                if (!patch.contains(x, z) || !isValidTurfBiome(level, x, z)) {
                    continue;
                }

                int generatedInColumn = 0;

                for (int y = upperY; y >= lowerY && generatedInColumn < TURF_MAX_DEPTH_PER_COLUMN; y--) {
                    pos.set(x, y, z);
                    BlockState currentState = chunk.getBlockState(pos);
                    pos.set(x, y + 1, z);
                    BlockState aboveState = chunk.getBlockState(pos);

                    if (canReplaceWithTurf(currentState, aboveState, generatedInColumn > 0)) {
                        pos.set(x, y, z);
                        chunk.setBlockState(pos, patch.state(), false);
                        generatedInColumn++;
                        replacedBlocks++;
                    } else if (generatedInColumn > 0 && currentState.isSolid()) {
                        break;
                    }
                }
            }
        }

        return replacedBlocks;
    }

    private static TurfPatch createTurfPatch(WorldGenLevel level, int originChunkX, int originChunkZ, BlockState turfState) {
        long seed = level.getSeed() ^ 0xD1B54A32D192ED03L;

        if (Math.floorMod(stableChunkHash(seed, originChunkX, originChunkZ), TURF_CHANCE) != 0) {
            return null;
        }

        int centerX = (originChunkX << 4) + 8;
        int centerZ = (originChunkZ << 4) + 8;

        return new TurfPatch(centerX, centerZ, turfState);
    }

    private static boolean isValidTurfBiome(WorldGenLevel level, int x, int z) {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));

        return biome.is(Biomes.SWAMP)
                || biome.is(Biomes.MANGROVE_SWAMP);
    }

    private static boolean canReplaceWithTurf(BlockState state, BlockState aboveState, boolean alreadyGenerating) {
        Block block = state.getBlock();

        if (block == Blocks.DIRT) {
            return alreadyGenerating || !isTurfBlockedByAboveBlock(aboveState);
        }

        return alreadyGenerating && block == Blocks.STONE;
    }

    private static boolean isTurfBlockedByAboveBlock(BlockState state) {
        Block block = state.getBlock();

        return state.is(BlockTags.LOGS)
                || block == Blocks.PUMPKIN
                || block == Blocks.CARVED_PUMPKIN
                || block == Blocks.MELON;
    }

    private static int generateBlackSand(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            BlockState sandReplacementState,
            BlockState redSandReplacementState
    ) {
        int replacedBlocks = 0;
        int upperY = Math.min(level.getSeaLevel() + 1, level.getMaxBuildHeight() - 1);
        int lowerY = Math.max(level.getSeaLevel() - 12, level.getMinBuildHeight());
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int originChunkX = chunkX - BLACK_SAND_SEARCH_DISTANCE_CHUNKS; originChunkX <= chunkX + BLACK_SAND_SEARCH_DISTANCE_CHUNKS; originChunkX++) {
            for (int originChunkZ = chunkZ - BLACK_SAND_SEARCH_DISTANCE_CHUNKS; originChunkZ <= chunkZ + BLACK_SAND_SEARCH_DISTANCE_CHUNKS; originChunkZ++) {
                BlackSandVein vein = createBlackSandVein(level, originChunkX, originChunkZ);

                if (vein == null || !vein.intersectsChunk(minX, minZ)) {
                    continue;
                }

                replacedBlocks += placeBlackSandVeinSlice(
                        level,
                        chunk,
                        pos,
                        minX,
                        minZ,
                        upperY,
                        lowerY,
                        vein,
                        sandReplacementState,
                        redSandReplacementState
                );
            }
        }

        return replacedBlocks;
    }

    private static int placeBlackSandVeinSlice(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int upperY,
            int lowerY,
            BlackSandVein vein,
            BlockState sandReplacementState,
            BlockState redSandReplacementState
    ) {
        int replacedBlocks = 0;

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;

            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;

                if (!vein.contains(x, z) || !isValidBlackSandPlacementBiome(level, x, z)) {
                    continue;
                }

                int generatedInColumn = 0;

                for (int y = upperY; y >= lowerY && generatedInColumn < BLACK_SAND_MAX_DEPTH_PER_COLUMN; y--) {
                    pos.set(x, y, z);
                    BlockState currentState = chunk.getBlockState(pos);

                    if (canReplaceWithBlackSand(currentState, sandReplacementState, redSandReplacementState)) {
                        chunk.setBlockState(pos, vein.state(), false);
                        generatedInColumn++;
                        replacedBlocks++;
                    } else if (generatedInColumn > 0 && currentState.isSolid()) {
                        break;
                    }
                }
            }
        }

        return replacedBlocks;
    }

    private static BlackSandVein createBlackSandVein(WorldGenLevel level, int originChunkX, int originChunkZ) {
        if (Math.floorMod(originChunkX, BLACK_SAND_GRID_SIZE_CHUNKS) != 0
                || Math.floorMod(originChunkZ, BLACK_SAND_GRID_SIZE_CHUNKS) != 0) {
            return null;
        }

        long seed = level.getSeed();
        int generationHash = stableChunkHash(seed, originChunkX, originChunkZ);

        if (Math.floorMod(generationHash, BLACK_SAND_CHANCE) != 0) {
            return null;
        }

        int offsetX = boundedHash(seed ^ 0x243F6A8885A308D3L, originChunkX, originChunkZ, -BLACK_SAND_RANDOM_OFFSET_BLOCKS, BLACK_SAND_RANDOM_OFFSET_BLOCKS);
        int offsetZ = boundedHash(seed ^ 0x13198A2E03707344L, originChunkX, originChunkZ, -BLACK_SAND_RANDOM_OFFSET_BLOCKS, BLACK_SAND_RANDOM_OFFSET_BLOCKS);
        int centerX = (originChunkX << 4) + 8 + offsetX;
        int centerZ = (originChunkZ << 4) + 8 + offsetZ;

        double radiusX = BLACK_SAND_PATCH_RADIUS_X + boundedHash(seed ^ 0xA4093822299F31D0L, originChunkX, originChunkZ, -5, 5);
        double radiusZ = BLACK_SAND_PATCH_RADIUS_Z + boundedHash(seed ^ 0x082EFA98EC4E6C89L, originChunkX, originChunkZ, -4, 4);
        BlockState state = selectBlackSandState(seed, originChunkX, originChunkZ);

        return new BlackSandVein(centerX, centerZ, radiusX, radiusZ, state);
    }

    private static boolean isValidBlackSandPlacementBiome(WorldGenLevel level, int x, int z) {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));

        return biome.is(BiomeTags.IS_RIVER)
                && !biome.is(BiomeTags.IS_OCEAN)
                && !biome.is(BiomeTags.IS_BEACH)
                && !biome.is(Biomes.SWAMP)
                && !biome.is(Biomes.MANGROVE_SWAMP);
    }

    private static BlockState selectBlackSandState(long seed, int originChunkX, int originChunkZ) {
        int type = Math.floorMod(stableChunkHash(seed ^ 0x6A09E667F3BCC909L, originChunkX, originChunkZ), 3);

        return switch (type) {
            case 1 -> findSandBlock("basaltic_black_sand").defaultBlockState();
            case 2 -> findSandBlock("granitic_black_sand").defaultBlockState();
            default -> findSandBlock("black_sand").defaultBlockState();
        };
    }

    private static boolean canReplaceWithBlackSand(BlockState state, BlockState sandReplacementState, BlockState redSandReplacementState) {
        Block block = state.getBlock();

        return BLACK_SAND_REPLACEABLE_BLOCKS.contains(block)
                || state.is(sandReplacementState.getBlock())
                || state.is(redSandReplacementState.getBlock());
    }

    private static int boundedHash(long seed, int chunkX, int chunkZ, int minInclusive, int maxInclusive) {
        return minInclusive + Math.floorMod(stableChunkHash(seed, chunkX, chunkZ), maxInclusive - minInclusive + 1);
    }

    private static int stableChunkHash(long seed, int chunkX, int chunkZ) {
        long value = seed;
        value ^= chunkX * 0x9E3779B97F4A7C15L;
        value ^= chunkZ * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;

        return (int) value;
    }

    private static Block findSandBlock(String id) {
        return GTBlocks.SAND_BLOCKS.entrySet().stream()
                .filter(entry -> id.equals(entry.getKey().id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing gt6uou:" + id + " sand block"))
                .getValue()
                .get();
    }

    private static Block findClayBlock(String id) {
        return GTBlocks.CLAY_BLOCKS.entrySet().stream()
                .filter(entry -> id.equals(entry.getKey().id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing gt6uou:" + id + " clay block"))
                .getValue()
                .get();
    }

    private static Block findOtherBlock(String id) {
        return GTBlocks.OTHER_BLOCKS.entrySet().stream()
                .filter(entry -> id.equals(entry.getKey().id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing gt6uou:" + id + " other block"))
                .getValue()
                .get();
    }

    private record ClayPit(int centerX, int centerZ, BlockState state) {
        private boolean contains(int x, int z) {
            double dx = x - centerX;
            double dz = z - centerZ;

            return (dx * dx) + (dz * dz) <= CLAY_PIT_RADIUS * CLAY_PIT_RADIUS;
        }

        private boolean intersectsChunk(int minX, int minZ) {
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            double closestX = Math.clamp(centerX, minX, maxX);
            double closestZ = Math.clamp(centerZ, minZ, maxZ);
            double dx = closestX - centerX;
            double dz = closestZ - centerZ;

            return (dx * dx) + (dz * dz) <= CLAY_PIT_RADIUS * CLAY_PIT_RADIUS;
        }
    }

    private record TurfPatch(int centerX, int centerZ, BlockState state) {
        private boolean contains(int x, int z) {
            double dx = x - centerX;
            double dz = z - centerZ;

            return (dx * dx) + (dz * dz) <= TURF_RADIUS * TURF_RADIUS;
        }

        private boolean intersectsChunk(int minX, int minZ) {
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            double closestX = Math.clamp(centerX, minX, maxX);
            double closestZ = Math.clamp(centerZ, minZ, maxZ);
            double dx = closestX - centerX;
            double dz = closestZ - centerZ;

            return (dx * dx) + (dz * dz) <= TURF_RADIUS * TURF_RADIUS;
        }
    }

    private record BlackSandVein(int centerX, int centerZ, double radiusX, double radiusZ, BlockState state) {
        private boolean contains(int x, int z) {
            double dx = x - centerX;
            double dz = z - centerZ;

            return (dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ) <= 1.0;
        }

        private boolean intersectsChunk(int minX, int minZ) {
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            double closestX = Math.clamp(centerX, minX, maxX);
            double closestZ = Math.clamp(centerZ, minZ, maxZ);
            double dx = closestX - centerX;
            double dz = closestZ - centerZ;

            return (dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ) <= 1.0;
        }
    }
}
