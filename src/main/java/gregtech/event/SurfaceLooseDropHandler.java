package gregtech.event;

import gregtech.block.SurfaceLooseBlock;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class SurfaceLooseDropHandler {
    private SurfaceLooseDropHandler() {
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof SurfaceLooseBlock block)) {
            return;
        }

        ItemStack stack = block.pickupStack(event.getLevel(), event.getPos());

        event.getDrops().clear();
        event.getDrops().add(new ItemEntity(
                event.getLevel(),
                event.getPos().getX() + 0.5D,
                event.getPos().getY() + 0.15D,
                event.getPos().getZ() + 0.5D,
                stack
        ));
    }
}
