package gregtech.dev.devtools;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Debug/reference handbook for opening the in-game geology guide.
 */
public class GeologyHandbookItem extends Item {
    public GeologyHandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (level.isClientSide()) {
            openClientScreen();
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Geology handbook");
    }

    private static void openClientScreen() {
        try {
            Class<?> hooks = Class.forName("gregtech.client.GeologyHandbookClient");
            hooks.getMethod("open").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to open geology handbook screen", exception);
        }
    }
}
