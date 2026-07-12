package gregtech.dev.devtools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Debug item for cutting a large vertical world slice to inspect generated rock strata.
 */
public class StrataCuttingToolItem extends Item {
    private static final int FORWARD_LENGTH_BLOCKS = 50;
    private static final int HALF_WIDTH_BLOCKS = 17;

    public StrataCuttingToolItem(Properties properties) {
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
            player.displayClientMessage(Component.literal("Strata cutter is a creative debug tool."), true);
            return InteractionResult.FAIL;
        }

        Direction forward = player.getDirection();
        if (forward.getAxis().isVertical()) {
            forward = Direction.NORTH;
        }

        Direction right = forward.getClockWise();
        BlockPos clickedPos = context.getClickedPos();
        int minY = serverLevel.getMinBuildHeight();
        int maxY = serverLevel.getMaxBuildHeight();
        CutResult result = cutSlice(serverLevel, clickedPos, forward, right, minY, maxY);

        player.displayClientMessage(
                Component.literal("Cut strata slice: removed " + result.removedBlocks() + " blocks, placed " + result.barrierBlocks() + " fluid barriers."),
                false
        );

        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Strata cutting tool");
    }

    private static CutResult cutSlice(
            ServerLevel level,
            BlockPos origin,
            Direction forward,
            Direction right,
            int minY,
            int maxY
    ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int removedBlocks = 0;
        int barrierBlocks = 0;

        for (int forwardOffset = 0; forwardOffset <= FORWARD_LENGTH_BLOCKS; forwardOffset++) {
            int centerX = origin.getX() + forward.getStepX() * forwardOffset;
            int centerZ = origin.getZ() + forward.getStepZ() * forwardOffset;

            for (int sideOffset = -HALF_WIDTH_BLOCKS; sideOffset <= HALF_WIDTH_BLOCKS; sideOffset++) {
                int x = centerX + right.getStepX() * sideOffset;
                int z = centerZ + right.getStepZ() * sideOffset;

                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);

                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 2);
                        removedBlocks++;
                    }
                }
            }
        }

        BlockState barrier = Blocks.BARRIER.defaultBlockState();

        for (int forwardOffset = 0; forwardOffset <= FORWARD_LENGTH_BLOCKS; forwardOffset++) {
            int centerX = origin.getX() + forward.getStepX() * forwardOffset;
            int centerZ = origin.getZ() + forward.getStepZ() * forwardOffset;

            for (int sideOffset = -HALF_WIDTH_BLOCKS; sideOffset <= HALF_WIDTH_BLOCKS; sideOffset++) {
                int x = centerX + right.getStepX() * sideOffset;
                int z = centerZ + right.getStepZ() * sideOffset;

                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);

                    if (level.getBlockState(pos).isAir() && hasFluidNeighbor(level, pos, minY, maxY)) {
                        level.setBlock(pos, barrier, 2);
                        barrierBlocks++;
                    }
                }
            }
        }

        return new CutResult(removedBlocks, barrierBlocks);
    }

    private static boolean hasFluidNeighbor(ServerLevel level, BlockPos pos, int minY, int maxY) {
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            int y = pos.getY() + direction.getStepY();
            if (y < minY || y >= maxY) {
                continue;
            }

            neighbor.set(
                    pos.getX() + direction.getStepX(),
                    y,
                    pos.getZ() + direction.getStepZ()
            );

            if (!level.getFluidState(neighbor).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private record CutResult(int removedBlocks, int barrierBlocks) {
    }
}
