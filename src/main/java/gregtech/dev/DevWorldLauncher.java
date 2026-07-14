package gregtech.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DevWorldLauncher {
    private static final DateTimeFormatter WORLD_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private DevWorldLauncher() {
    }

    public static void createDevWorld(Screen returnScreen) {
        createDevWorld(returnScreen, false);
    }

    public static void createEarthDevWorld(Screen returnScreen) {
        createDevWorld(returnScreen, true);
    }

    public static void createEarthDevWorld(Screen returnScreen, long seed) {
        createDevWorld(returnScreen, true, new WorldOptions(seed, true, false));
    }

    private static void createDevWorld(Screen returnScreen, boolean earthWorld) {
        createDevWorld(returnScreen, earthWorld, WorldOptions.defaultWithRandomSeed());
    }

    private static void createDevWorld(Screen returnScreen, boolean earthWorld, WorldOptions worldOptions) {
        Minecraft minecraft = Minecraft.getInstance();
        String suffix = LocalDateTime.now().format(WORLD_NAME_FORMAT);
        String levelId = earthWorld ? "GT6UOU_Earth_" + suffix : "GT6UOU_Dev_" + suffix;
        String levelName = earthWorld ? "GT6UOU Earth " + suffix : "GT6UOU Dev " + suffix;
        LevelSettings settings = new LevelSettings(
                levelName,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT
        );

        if (earthWorld) {
            DevWorldGiftHandler.markEarthDevWorld(levelName);
        } else {
            DevWorldGiftHandler.markDevWorld(levelName);
        }
        minecraft.createWorldOpenFlows().createFreshLevel(
                levelId,
                settings,
                worldOptions,
                WorldPresets::createNormalWorldDimensions,
                returnScreen
        );
    }

    public static Component buttonLabel() {
        return Component.literal("GT6UOU Dev World");
    }

    public static Component earthButtonLabel() {
        return Component.literal("GT Earth Dev World");
    }
}
