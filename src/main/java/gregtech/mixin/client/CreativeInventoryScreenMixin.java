package gregtech.mixin.client;

import gregtech.client.earth.InventoryInfoTab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the information tabs available while testing in creative mode. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    protected CreativeInventoryScreenMixin(
            CreativeModeInventoryScreen.ItemPickerMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gt6uou$addInformationTabs(CallbackInfo callbackInfo) {
        int x = Math.min(leftPos + imageWidth, width - 24);
        InventoryInfoTab.createTabs(x, topPos + 8, (Screen) (Object) this, null)
                .forEach(this::addRenderableWidget);
    }
}
