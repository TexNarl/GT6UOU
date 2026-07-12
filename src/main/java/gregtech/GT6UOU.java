package gregtech;

import com.mojang.logging.LogUtils;
import gregtech.creativetabs.GTCreativeTabs;
import gregtech.dev.DevWorldGiftHandler;
import gregtech.registry.GTBlocks;
import gregtech.registry.GTFluids;
import gregtech.registry.GTItems;
import gregtech.event.OreDropHandler;
import gregtech.event.SurfaceLooseDropHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(GT6UOU.MODID)
public final class GT6UOU {
    public static final String MODID = "gt6uou";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GT6UOU(IEventBus modEventBus, ModContainer modContainer) {
        GTFluids.register(modEventBus);
        GTBlocks.register(modEventBus);
        GTItems.register(modEventBus);
        GTCreativeTabs.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(DevWorldGiftHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(OreDropHandler::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(SurfaceLooseDropHandler::onBlockDrops);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("GT6UOU loaded");
    }
}
