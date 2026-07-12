package gregtech.dev.devtools;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Debug item for quickly setting server time to day.
 */
public class DayClockItem extends Item {
    private static final long DAY_TIME = 1000L;

    public DayClockItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!player.isCreative()) {
            player.displayClientMessage(Component.literal("Day clock is a creative debug tool."), true);
            return InteractionResultHolder.fail(stack);
        }

        MinecraftServer server = serverLevel.getServer();
        for (ServerLevel loadedLevel : server.getAllLevels()) {
            loadedLevel.setDayTime(DAY_TIME);
        }

        player.displayClientMessage(Component.literal("Time set to day."), true);

        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Day clock");
    }
}
