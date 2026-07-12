package gregtech;

import gregtech.registry.GTBlocks;
import gregtech.registry.GTFluids;
import gregtech.registry.GTItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = GT6UOU.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = GT6UOU.MODID, value = Dist.CLIENT)
public class GT6UOUClient {
    public GT6UOUClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        GT6UOU.LOGGER.info("HELLO FROM CLIENT SETUP");
        GT6UOU.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(GTFluids.RAW_OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.FLOWING_RAW_OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.FLOWING_OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.LIGHT_OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.FLOWING_LIGHT_OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.HEAVY_OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.FLOWING_HEAVY_OIL.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.SALT_WATER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.FLOWING_SALT_WATER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.NATURAL_GAS.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GTFluids.FLOWING_NATURAL_GAS.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        GTBlocks.ROCK_BLOCKS.forEach((rock, block) ->
                event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? rock.tintColor() : 0xFFFFFF, block.get())
        );
        GTBlocks.CLAY_BLOCKS.forEach((clay, block) ->
                event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? clay.tintColor() : 0xFFFFFF, block.get())
        );
        GTBlocks.SAND_BLOCKS.forEach((sand, block) ->
                event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? sand.tintColor() : 0xFFFFFF, block.get())
        );
        GTBlocks.OTHER_BLOCKS.forEach((other, block) ->
                event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? other.tintColor() : 0xFFFFFF, block.get())
        );
    }

    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        GTItems.ROCK_ITEMS.forEach((rock, item) ->
                event.register((stack, tintIndex) -> tintIndex == 0 ? rock.tintColor() : 0xFFFFFF, item.get())
        );
        GTItems.CLAY_ITEMS.forEach((clay, item) ->
                event.register((stack, tintIndex) -> tintIndex == 0 ? clay.tintColor() : 0xFFFFFF, item.get())
        );
        GTItems.SAND_ITEMS.forEach((sand, item) ->
                event.register((stack, tintIndex) -> tintIndex == 0 ? sand.tintColor() : 0xFFFFFF, item.get())
        );
        GTItems.OTHER_ITEMS.forEach((other, item) ->
                event.register((stack, tintIndex) -> tintIndex == 0 ? other.tintColor() : 0xFFFFFF, item.get())
        );
    }
}
