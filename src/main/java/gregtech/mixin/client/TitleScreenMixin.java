package gregtech.mixin.client;

import gregtech.dev.DevWorldLauncher;
import gregtech.client.earth.EarthCreateWorldPreviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gt6uou$addDevWorldButton(CallbackInfo callbackInfo) {
        this.addRenderableWidget(Button.builder(
                        DevWorldLauncher.buttonLabel(),
                        button -> DevWorldLauncher.createDevWorld((Screen) (Object) this)
                )
                .bounds(8, 8, 140, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                        DevWorldLauncher.earthButtonLabel(),
                        button -> Minecraft.getInstance().setScreen(
                                new EarthCreateWorldPreviewScreen((Screen) (Object) this)
                        )
                )
                .bounds(8, 32, 140, 20)
                .build());
    }
}
