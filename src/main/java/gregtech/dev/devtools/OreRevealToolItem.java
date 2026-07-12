package gregtech.dev.devtools;

import gregapi.code.OreBlockForm;
import gregapi.data.NaturalResourceList;
import gregapi.tools.ScannerList;
import gregtech.GT6UOU;
import gregtech.registry.GTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Debug item for revealing mineral resource distribution inside a 3x3 chunk area.
 *
 * <p>Right click any block: the clicked block's chunk becomes the center, and
 * every non-resource block in that 3x3 chunk square is replaced with air.</p>
 */
public class OreRevealToolItem extends Item {
    private static final int CHUNK_RADIUS = 1;

    public OreRevealToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.PASS;
        }
        if (!player.isCreative()) {
            player.displayClientMessage(Component.literal("Ore reveal tool is a creative debug tool."), true);
            return InteractionResult.FAIL;
        }

        ChunkPos centerChunk = new ChunkPos(context.getClickedPos());
        int minY = serverLevel.getMinBuildHeight();
        int maxY = serverLevel.getMaxBuildHeight();
        RevealResult result = revealOres(serverLevel, centerChunk, minY, maxY);

        player.displayClientMessage(
                Component.literal("Ore reveal: cleared " + result.removedBlocks()
                        + " non-resource blocks in 3x3 chunks, kept " + result.keptOreBlocks() + " resource blocks."),
                false
        );

        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Ore reveal tool");
    }

    private static RevealResult revealOres(ServerLevel level, ChunkPos centerChunk, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int removedBlocks = 0;
        int keptOreBlocks = 0;

        for (int chunkX = centerChunk.x - CHUNK_RADIUS; chunkX <= centerChunk.x + CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = centerChunk.z - CHUNK_RADIUS; chunkZ <= centerChunk.z + CHUNK_RADIUS; chunkZ++) {
                int minX = chunkX << 4;
                int minZ = chunkZ << 4;

                for (int localX = 0; localX < 16; localX++) {
                    int x = minX + localX;

                    for (int localZ = 0; localZ < 16; localZ++) {
                        int z = minZ + localZ;

                        for (int y = minY; y < maxY; y++) {
                            pos.set(x, y, z);
                            BlockState state = level.getBlockState(pos);

                            if (state.isAir()) {
                                continue;
                            }
                            if (isGtMineralResource(state)) {
                                keptOreBlocks++;
                                continue;
                            }

                            level.setBlock(pos, air, 2);
                            removedBlocks++;
                        }
                    }
                }
            }
        }

        return new RevealResult(removedBlocks, keptOreBlocks);
    }

    private static boolean isGtOre(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        return GT6UOU.MODID.equals(key.getNamespace()) && OreBlockForm.isOreLikeBlockId(key.getPath());
    }

    private static boolean isGtMineralResource(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        if (!GT6UOU.MODID.equals(key.getNamespace())) {
            return false;
        }

        if (GTBlocks.ROCK_BLOCKS.values().stream().anyMatch(block -> state.is(block.get()))) {
            return false;
        }

        if (isGtOre(state)) {
            return true;
        }

        String resourceId = OreBlockForm.mineralIdFromBlockId(key.getPath());

        return NaturalResourceList.byId(resourceId)
                .map(resource -> resource.category() == ScannerList.DEPOSIT)
                .orElse(false);
    }

    private record RevealResult(int removedBlocks, int keptOreBlocks) {
    }
}
