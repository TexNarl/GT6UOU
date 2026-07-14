package gregtech.mixin.client;

import gregtech.client.earth.InventoryInfoTab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds calendar, climate and handbook tabs to the normal survival inventory. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {
    protected InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gt6uou$addInformationTabs(CallbackInfo callbackInfo) {
        int x = Math.min(leftPos + imageWidth, width - 24);
        InventoryInfoTab.createTabs(x, topPos + 8, (Screen) (Object) this, null)
                .forEach(this::addRenderableWidget);
    }
}
