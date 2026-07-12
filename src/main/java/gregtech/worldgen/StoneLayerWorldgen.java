package gregtech.worldgen;

import gregapi.data.StoneLayerList;
import gregtech.registry.GTBlocks;
import gregtech.worldgen.sampler.StoneLayerStackSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Generates the solid underground part of the Overworld: GT6UOU stone layers
 * and ore veins hosted by those layers.
 */
public final class StoneLayerWorldgen {
    private static final int STONE_LAYER_SURFACE_SMOOTHING_RADIUS = 5;

    private static final Set<Block> VANILLA_STONE_LAYER_REPLACEABLE_BLOCKS = Set.of(
            Blocks.CLAY,
            Blocks.STONE,
            Blocks.DEEPSLATE,
            Blocks.DIORITE,
            Blocks.ANDESITE,
            Blocks.GRANITE,
            Blocks.TUFF,
            Blocks.COAL_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE
    );

    private StoneLayerWorldgen() {
    }

    public static int generate(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        if (!GTDimensions.isEarthLike(level)) {
            return 0;
        }

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int generatedBlocks = 0;

        generatedBlocks += generateStoneLayers(
                chunk,
                pos,
                minX,
                minZ,
                minY,
                maxY,
                new StoneLayerStackSampler(level.getSeed()),
                findStoneLayerStates()
        );
        generatedBlocks += OreVeinWorldgen.generateOverworldOreVeins(level, chunk, pos);

        return generatedBlocks;
    }

    private static int generateStoneLayers(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int minY,
            int maxY,
            StoneLayerStackSampler sampler,
            Map<String, BlockState> stoneLayerStates
    ) {
        int replacedBlocks = 0;

        if (stoneLayerStates.isEmpty()) {
            throw new IllegalStateException("No GT6UOU stone layer states registered");
        }

        int[][] surfaceHeights = findChunkSurfaceHeights(chunk, pos, minX, minZ, minY, maxY);

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;

            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                int surfaceY = smoothSurfaceY(surfaceHeights, localX, localZ);

                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    BlockState currentState = chunk.getBlockState(pos);

                    if (VANILLA_STONE_LAYER_REPLACEABLE_BLOCKS.contains(currentState.getBlock())) {
                        String rockId = sampler.sampleRockId(x, y, z, surfaceY);
                        BlockState stoneLayerState = stoneLayerStates.get(rockId);

                        if (stoneLayerState == null) {
                            throw new IllegalStateException("Missing GT6UOU stone layer block state for rock: " + rockId);
                        }

                        chunk.setBlockState(pos, stoneLayerState, false);
                        replacedBlocks++;
                    }
                }
            }
        }

        return replacedBlocks;
    }

    private static int[][] findChunkSurfaceHeights(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int minY,
            int maxY
    ) {
        int[][] surfaceHeights = new int[16][16];

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;

            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;

                surfaceHeights[localX][localZ] = findColumnSurfaceY(chunk, pos, x, z, minY, maxY);
            }
        }

        return surfaceHeights;
    }

    private static int smoothSurfaceY(int[][] surfaceHeights, int localX, int localZ) {
        int weightedHeight = 0;
        int totalWeight = 0;

        for (int dx = -STONE_LAYER_SURFACE_SMOOTHING_RADIUS; dx <= STONE_LAYER_SURFACE_SMOOTHING_RADIUS; dx++) {
            int sampleX = Math.clamp(localX + dx, 0, 15);

            for (int dz = -STONE_LAYER_SURFACE_SMOOTHING_RADIUS; dz <= STONE_LAYER_SURFACE_SMOOTHING_RADIUS; dz++) {
                int sampleZ = Math.clamp(localZ + dz, 0, 15);
                int distance = Math.abs(dx) + Math.abs(dz);
                int weight = STONE_LAYER_SURFACE_SMOOTHING_RADIUS + 1 - Math.min(STONE_LAYER_SURFACE_SMOOTHING_RADIUS, distance);

                weightedHeight += surfaceHeights[sampleX][sampleZ] * weight;
                totalWeight += weight;
            }
        }

        return Math.round(weightedHeight / (float) totalWeight);
    }

    private static int findColumnSurfaceY(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            int minY,
            int maxY
    ) {
        for (int y = maxY - 1; y >= minY; y--) {
            pos.set(x, y, z);

            if (!chunk.getBlockState(pos).isAir()) {
                return y;
            }
        }

        return minY;
    }

    private static Block findRockBlock(String id) {
        return GTBlocks.ROCK_BLOCKS.entrySet().stream()
                .filter(entry -> id.equals(entry.getKey().id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing gt6uou:" + id + " rock block"))
                .getValue()
                .get();
    }

    private static Map<String, BlockState> findStoneLayerStates() {
        Map<String, BlockState> states = new LinkedHashMap<>();

        for (var layer : StoneLayerList.OVERWORLD_LAYERS) {
            states.put(layer.rockId(), findRockBlock(layer.rockId()).defaultBlockState());
        }

        return Map.copyOf(states);
    }
}
