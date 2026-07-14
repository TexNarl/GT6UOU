package gregtech.creativetabs;

import gregtech.GT6UOU;
import gregtech.registry.GTBlocks;
import gregtech.registry.GTItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GTCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GT6UOU.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GT6UOU_TAB = CREATIVE_TABS.register("gt6uou", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("GT6UOU"))
                    .icon(() -> new ItemStack(GTItems.ROCK_ITEMS.values().iterator().next().get()))
                    .displayItems((parameters, output) -> {
                        GTItems.ROCK_ITEMS.values().forEach(output::accept);
                        GTItems.CLAY_ITEMS.values().forEach(output::accept);
                        GTItems.SAND_ITEMS.values().forEach(output::accept);
                        GTItems.OTHER_ITEMS.values().forEach(output::accept);
                        output.accept(GTItems.RAW_OIL_BUCKET.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEV_TOOLS_TAB = CREATIVE_TABS.register("dev_tools", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("Dev tools"))
                    .icon(() -> new ItemStack(GTItems.STRATA_CUTTING_TOOL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(GTItems.STRATA_CUTTING_TOOL.get());
                        output.accept(GTItems.DAY_CLOCK.get());
                        output.accept(GTItems.MINERAL_SCANNER.get());
                        output.accept(GTItems.ORE_REVEAL_TOOL.get());
                        output.accept(GTItems.SURFACE_STICK.get());
                        output.accept(GTItems.SURFACE_ROCK.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORES_TAB = CREATIVE_TABS.register("ores", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("Ores"))
                    .icon(() -> new ItemStack(Items.IRON_ORE))
                    .displayItems((parameters, output) ->
                            GTItems.ORE_FORM_ITEMS.values().forEach(items -> items.values().forEach(output::accept))
                    )
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS_TAB = CREATIVE_TABS.register("materials", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("Materials"))
                    .icon(() -> new ItemStack(Items.RAW_IRON))
                    .displayItems((parameters, output) ->
                            GTItems.ORE_DROP_ITEMS.values().forEach(items -> items.values().forEach(output::accept))
                    )
                    .build()
    );

    private GTCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
