package gregtech.worldgen.earth.preview;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import gregtech.GT6UOU;
import gregapi.data.DimensionList;
import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.GTEarthChunkGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Development command for validating the regional and weighted-height maps.
 * The same renderer will back the future create-world preview UI.
 */
public final class EarthPreviewCommand {
    private static final int DEFAULT_PIXELS = 512;
    private static final int DEFAULT_BLOCKS_PER_PIXEL = 32;
    private static final AtomicBoolean RENDERING = new AtomicBoolean();

    private EarthPreviewCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("gt6uou")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("earth_preview")
                        .executes(context -> render(
                                context.getSource(),
                                DEFAULT_PIXELS,
                                DEFAULT_BLOCKS_PER_PIXEL
                        ))
                        .then(Commands.argument("pixels", IntegerArgumentType.integer(128, 2048))
                                .executes(context -> render(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "pixels"),
                                        DEFAULT_BLOCKS_PER_PIXEL
                                ))
                                .then(Commands.argument(
                                                "blocks_per_pixel",
                                                IntegerArgumentType.integer(4, 256)
                                        )
                                        .executes(context -> render(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "pixels"),
                                                IntegerArgumentType.getInteger(context, "blocks_per_pixel")
                                        ))))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.LAND))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.RIVERS_AND_MOUNTAINS))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.INLAND_HEIGHT))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.MOUNTAINS))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.GEOLOGY))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.ROCKS))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.SHORES))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.RIVERS))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.TEMPERATURE))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.RAINFALL))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.BIOMES))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.CLIMATE_RESTRICTED_GENERATION))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.BIOME_ALTITUDE))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.HOTSPOTS))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.KARST))
                        .then(layerLiteral(EarthPreviewService.PreviewLayer.TERRAIN))));
        dispatcher.register(Commands.literal("gt6uou")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("earth_climate")
                        .executes(context -> climate(context.getSource()))));
    }

    private static int climate(CommandSourceStack source) {
        ServerLevel earth = source.getServer().getLevel(DimensionList.EARTH);
        if (earth == null
                || !(earth.getChunkSource().getGenerator() instanceof GTEarthChunkGenerator generator)
                || generator.earthState() == null) {
            source.sendFailure(Component.literal("GT6UOU Earth generator state is not available"));
            return 0;
        }
        int x = (int) Math.floor(source.getPosition().x);
        int y = (int) Math.floor(source.getPosition().y);
        int z = (int) Math.floor(source.getPosition().z);
        var state = generator.earthState();
        var mean = state.climateModel().sample(x, z);
        var now = state.seasonalClimate().sample(x, y, z, earth.getDayTime());
        source.sendSuccess(() -> Component.literal(String.format(
                java.util.Locale.ROOT,
                "Earth climate at %d, %d, %d: mean %.1f C / %.0f mm; now %.1f C / %.0f mm; %s; precipitation %s (%.2f)",
                x, y, z,
                mean.averageTemperature(), mean.averageRainfall(),
                now.temperature(), now.rainfall(), now.season(),
                now.precipitating() ? "yes" : "no", now.precipitationIntensity()
        )), false);
        return 1;
    }

    private static int render(CommandSourceStack source, int pixels, int blocksPerPixel) {
        return render(source, pixels, blocksPerPixel, EarthPreviewService.PreviewLayer.TERRAIN);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> layerLiteral(
            EarthPreviewService.PreviewLayer layer
    ) {
        return Commands.literal(layer.serializedName())
                .executes(context -> render(
                        context.getSource(),
                        DEFAULT_PIXELS,
                        DEFAULT_BLOCKS_PER_PIXEL,
                        layer
                ))
                .then(Commands.argument("pixels", IntegerArgumentType.integer(128, 2048))
                        .executes(context -> render(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "pixels"),
                                DEFAULT_BLOCKS_PER_PIXEL,
                                layer
                        ))
                        .then(Commands.argument(
                                        "blocks_per_pixel",
                                        IntegerArgumentType.integer(4, 256)
                                )
                                .executes(context -> render(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "pixels"),
                                        IntegerArgumentType.getInteger(context, "blocks_per_pixel"),
                                        layer
                                ))));
    }

    private static int render(
            CommandSourceStack source,
            int pixels,
            int blocksPerPixel,
            EarthPreviewService.PreviewLayer layer
    ) {
        MinecraftServer server = source.getServer();
        ServerLevel earth = server.getLevel(DimensionList.EARTH);
        if (earth == null
                || !(earth.getChunkSource().getGenerator() instanceof GTEarthChunkGenerator generator)
                || generator.earthState() == null) {
            source.sendFailure(Component.literal("GT6UOU Earth generator state is not available"));
            return 0;
        }
        if (!RENDERING.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("An Earth preview is already rendering"));
            return 0;
        }

        EarthGeneratorState state = generator.earthState();
        int centerX = (int) Math.floor(source.getPosition().x);
        int centerZ = (int) Math.floor(source.getPosition().z);
        source.sendSuccess(() -> Component.literal(
                "Rendering " + pixels + "x" + pixels + " Earth "
                        + layer.serializedName() + " preview..."
        ), false);

        CompletableFuture.supplyAsync(() -> {
            try {
                return EarthPreviewService.renderToWorldFolder(
                        server,
                        state,
                        centerX,
                        centerZ,
                        pixels,
                        blocksPerPixel,
                        layer
                );
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((path, error) -> server.execute(() -> {
            RENDERING.set(false);
            if (error != null) {
                GT6UOU.LOGGER.error("Failed to render Earth preview", error);
                source.sendFailure(Component.literal("Earth preview failed; see the log"));
                return;
            }
            Path relative = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .relativize(path);
            source.sendSuccess(() -> Component.literal("Earth preview saved to " + relative), false);
        }));
        return 1;
    }
}
