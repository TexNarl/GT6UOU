package gregtech.worldgen.earth.preview;

import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.climate.EarthBiomeSemantics;
import gregtech.worldgen.earth.region.EarthRegion;
import gregtech.worldgen.earth.region.EarthRegionGenerator;
import gregtech.worldgen.earth.region.EarthUnits;
import gregtech.worldgen.earth.river.EarthRiverSampler.RiverInfo;
import gregtech.worldgen.earth.terrain.EarthShoreProfiles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Shared renderer for debug PNGs and the future create-world preview screen.
 * It samples the exact state used by the loaded Earth dimension.
 */
public final class EarthPreviewService {
    private EarthPreviewService() {
    }

    public static BufferedImage render(
            EarthGeneratorState state,
            int centerBlockX,
            int centerBlockZ,
            int pixels,
            int blocksPerPixel
    ) {
        return render(state, centerBlockX, centerBlockZ, pixels, blocksPerPixel, PreviewLayer.TERRAIN);
    }

    public static BufferedImage render(
            EarthGeneratorState state,
            int centerBlockX,
            int centerBlockZ,
            int pixels,
            int blocksPerPixel,
            PreviewLayer layer
    ) {
        return render(state, centerBlockX, centerBlockZ, pixels, blocksPerPixel, layer, () -> false);
    }

    /**
     * Renders through the same sampler as runtime terrain while allowing a UI
     * request to stop obsolete work after the seed, center, scale or layer is
     * changed.
     */
    public static BufferedImage render(
            EarthGeneratorState state,
            int centerBlockX,
            int centerBlockZ,
            int pixels,
            int blocksPerPixel,
            PreviewLayer layer,
            BooleanSupplier cancelled
    ) {
        EarthRegionGenerator generator = state.regionGenerator();
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        int halfSpan = pixels * blocksPerPixel / 2;

        for (int pixelZ = 0; pixelZ < pixels; pixelZ++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Earth preview request was superseded");
            }
            int blockZ = centerBlockZ - halfSpan + pixelZ * blocksPerPixel;
            double gridZ = EarthUnits.blockToGridExact(blockZ);

            for (int pixelX = 0; pixelX < pixels; pixelX++) {
                int blockX = centerBlockX - halfSpan + pixelX * blocksPerPixel;
                double gridX = EarthUnits.blockToGridExact(blockX);
                EarthRegion.Point point = generator.sampleSemantic(gridX, gridZ);
                image.setRGB(pixelX, pixelZ, layer.color(state, blockX, blockZ, point));
            }
        }
        return image;
    }

    public static Path renderToWorldFolder(
            MinecraftServer server,
            EarthGeneratorState state,
            int centerBlockX,
            int centerBlockZ,
            int pixels,
            int blocksPerPixel
    ) throws IOException {
        return renderToWorldFolder(
                server, state, centerBlockX, centerBlockZ,
                pixels, blocksPerPixel, PreviewLayer.TERRAIN
        );
    }

    public static Path renderToWorldFolder(
            MinecraftServer server,
            EarthGeneratorState state,
            int centerBlockX,
            int centerBlockZ,
            int pixels,
            int blocksPerPixel,
            PreviewLayer layer
    ) throws IOException {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("earth-previews");
        Files.createDirectories(directory);
        String fileName = "earth_seed_" + state.worldSeed()
                + "_x_" + centerBlockX
                + "_z_" + centerBlockZ
                + "_" + layer.serializedName()
                + "_" + pixels + "px_"
                + blocksPerPixel + "bpp.png";
        Path target = directory.resolve(fileName);
        BufferedImage image = render(
                state, centerBlockX, centerBlockZ,
                pixels, blocksPerPixel, layer
        );
        if (!ImageIO.write(image, "PNG", target.toFile())) {
            throw new IOException("No PNG writer is available");
        }
        return target;
    }

    private static int argb(int red, int green, int blue) {
        return 0xFF000000
                | (Math.clamp(red, 0, 255) << 16)
                | (Math.clamp(green, 0, 255) << 8)
                | Math.clamp(blue, 0, 255);
    }

    /** Independently inspectable outputs of the regional TFG task chain. */
    public enum PreviewLayer {
        RIVERS_AND_MOUNTAINS("rivers_and_mountains") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                RiverInfo river = state.riverSampler().sample(blockX, blockZ);
                if (river != null && river.normalizedDistanceSq() <= 1.0D) {
                    return argb(22, 217, 236);
                }
                if (point.coastalMountain()) {
                    return argb(244, 102, 57);
                }
                if (point.mountain()) {
                    return argb(224, 224, 212);
                }
                if (point.lake()) {
                    return argb(105, 112, 205);
                }
                return point.land() ? argb(31, 126, 44) : argb(53, 65, 166);
            }
        },
        ROCKS("rocks") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                // The first member of the selected stack is the surface rock.
                // Avoid running the much heavier physical-height sampler for
                // every preview pixel when the stack already owns this value.
                String rock = state.stoneLayerSampler().sampleStack(blockX, blockZ).rockIds().getFirst();
                return rockColor(rock);
            }
        },
        CLIMATE_RESTRICTED_GENERATION("climate_restricted_generation") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                return climateRestriction(state, blockX, blockZ).color();
            }
        },
        BIOME_ALTITUDE("biome_altitude") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                if (!point.land()) {
                    return point.distanceToLand() > 5 ? argb(48, 57, 153) : argb(83, 105, 195);
                }
                return switch (Math.clamp(point.discreteBiomeAltitude(), 0, 3)) {
                    case 0 -> argb(19, 105, 32);
                    case 1 -> argb(34, 139, 48);
                    case 2 -> argb(65, 178, 75);
                    default -> argb(112, 216, 112);
                };
            }
        },
        LAND("land") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                if (point.island()) {
                    return argb(104, 151, 70);
                }
                if (point.land()) {
                    int inland = Math.clamp(point.baseLandHeight(), 0, 18);
                    return argb(58 + inland * 3, 112 + inland * 4, 54 + inland);
                }
                int depth = Math.clamp(point.distanceToLand(), 0, 14);
                return argb(27 - depth, 92 - depth * 3, 155 - depth * 3);
            }
        },
        INLAND_HEIGHT("height") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                if (!point.land()) {
                    int depth = Math.clamp(point.distanceToLand(), 0, 14);
                    return argb(71 - depth * 2, 103 - depth * 3, 195 - depth * 3);
                }
                int inland = Math.clamp(point.baseLandHeight(), 0, 18);
                return argb(22 + inland * 3, 91 + inland * 7, 31 + inland * 3);
            }
        },
        MOUNTAINS("mountains") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                if (point.coastalMountain()) {
                    return argb(231, 127, 46);
                }
                if (point.mountain()) {
                    return argb(246, 242, 221);
                }
                return point.land() ? argb(75, 91, 61) : argb(23, 63, 105);
            }
        },
        GEOLOGY("geology") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                return switch (point.geologicalProvince()) {
                    case CONTINENTAL_SEDIMENTARY -> argb(151, 126, 74);
                    case VOLCANIC -> argb(174, 66, 47);
                    case UPLIFT -> argb(126, 148, 174);
                    case MAFIC_DEEP -> argb(43, 49, 59);
                };
            }
        },
        SHORES("shores") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                EarthRegion.TerrainProfile profile = state.terrainProfiles()
                        .profileAtBlock(blockX, blockZ);
                return switch (profile) {
                    case SHORE -> argb(238, 209, 135);
                    case TIDAL_FLATS -> argb(184, 170, 111);
                    case COASTAL_DUNES -> argb(222, 188, 102);
                    case EMBAYMENTS -> argb(151, 139, 105);
                    case ROCKY_SHORES -> argb(102, 105, 108);
                    case DEEP_OCEAN -> argb(15, 37, 67);
                    case OCEAN -> argb(27, 73, 116);
                    case LAKE -> argb(42, 129, 173);
                    case VOLCANO -> argb(124, 48, 38);
                    default -> argb(67, 92, 59);
                };
            }
        },
        RIVERS("rivers") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                RiverInfo info = state.riverSampler().sample(blockX, blockZ);
                if (info != null) {
                    double normalized = info.normalizedDistanceSq();
                    if (normalized <= 1.0) return argb(17, 224, 245);
                    if (normalized <= 2.5) return argb(35, 137, 184);
                }
                EarthRegion.TerrainProfile profile = state.terrainProfiles()
                        .profileAtBlock(blockX, blockZ);
                if (EarthShoreProfiles.isShore(profile)) return argb(142, 126, 86);
                return profile == EarthRegion.TerrainProfile.DEEP_OCEAN
                        || profile == EarthRegion.TerrainProfile.OCEAN
                        ? argb(18, 48, 80)
                        : argb(48, 61, 44);
            }
        },
        TEMPERATURE("temperature") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                float temperature = state.climateModel().averageTemperature(blockX, blockZ);
                return climateGradient(
                        temperature,
                        new float[] {-20.0F, -10.0F, 0.0F, 10.0F, 20.0F, 30.0F},
                        new int[] {
                                argb(38, 63, 138),
                                argb(49, 137, 202),
                                argb(96, 186, 166),
                                argb(171, 196, 91),
                                argb(231, 157, 63),
                                argb(181, 55, 43)
                        }
                );
            }
        },
        RAINFALL("rainfall") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                float rainfall = state.climateModel().averageRainfall(blockX, blockZ);
                return climateGradient(
                        rainfall,
                        new float[] {0.0F, 60.0F, 155.0F, 250.0F, 375.0F, 500.0F},
                        new int[] {
                                argb(203, 170, 94),
                                argb(194, 157, 73),
                                argb(164, 174, 78),
                                argb(83, 157, 91),
                                argb(47, 137, 125),
                                argb(38, 88, 151)
                        }
                );
            }
        },
        BIOMES("biomes") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                var profile = state.terrainProfiles().profileAtBlock(blockX, blockZ);
                var climate = state.climateModel().sample(blockX, blockZ);
                var biome = EarthBiomeSemantics.resolve(
                        profile,
                        climate.averageTemperature(),
                        climate.averageRainfall(),
                        state.riverSampler().sample(blockX, blockZ) != null,
                        state.karstModel().isKarst(blockX, blockZ)
                ).location().getPath();
                int hash = biome.hashCode();
                return argb(64 + Math.floorMod(hash, 160),
                        64 + Math.floorMod(hash >> 8, 160),
                        64 + Math.floorMod(hash >> 16, 160));
            }
        },
        HOTSPOTS("hotspots") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                return switch (point.hotSpotAge()) {
                    case 1 -> argb(245, 58, 32);
                    case 2 -> argb(225, 115, 42);
                    case 3 -> argb(145, 88, 62);
                    case 4 -> argb(91, 78, 73);
                    default -> point.land() ? argb(69, 92, 58) : argb(20, 58, 102);
                };
            }
        },
        KARST("karst") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                if (!state.karstModel().isKarst(blockX, blockZ)) {
                    return point.land() ? argb(57, 66, 50) : argb(18, 48, 82);
                }
                float rain = state.climateModel().averageRainfall(blockX, blockZ);
                if (rain > 425.0F) return argb(184, 235, 128);
                if (rain > 375.0F) return argb(148, 205, 107);
                if (rain > 250.0F) return argb(107, 169, 91);
                return argb(126, 119, 91);
            }
        },
        TERRAIN("terrain") {
            @Override
            int color(EarthGeneratorState state, int blockX, int blockZ, EarthRegion.Point point) {
                return switch (state.terrainProfiles().profileAtBlock(blockX, blockZ)) {
                    case DEEP_OCEAN -> argb(16, 47, 91);
                    case OCEAN -> argb(31, 92, 151);
                    case ISLAND -> argb(105, 159, 76);
                    case PLAINS -> argb(91, 151, 70);
                    case HILLS -> argb(116, 142, 71);
                    case ROLLING_HILLS -> argb(133, 131, 76);
                    case HIGHLANDS -> argb(137, 112, 82);
                    case MOUNTAINS -> argb(202, 199, 190);
                    case COASTAL_MOUNTAINS -> argb(184, 125, 83);
                    case SHORE -> argb(238, 209, 135);
                    case TIDAL_FLATS -> argb(184, 170, 111);
                    case COASTAL_DUNES -> argb(222, 188, 102);
                    case EMBAYMENTS -> argb(151, 139, 105);
                    case ROCKY_SHORES -> argb(102, 105, 108);
                    case LAKE -> argb(42, 129, 173);
                    case VOLCANO -> argb(124, 48, 38);
                };
            }
        };

        private final String serializedName;

        PreviewLayer(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public String displayName() {
            return switch (this) {
                case RIVERS_AND_MOUNTAINS -> "Rivers and Mountains";
                case GEOLOGY -> "Rock Types";
                case ROCKS -> "Rocks";
                case BIOMES -> "Biomes";
                case RAINFALL -> "Rainfall";
                case TEMPERATURE -> "Temperature";
                case CLIMATE_RESTRICTED_GENERATION -> "Climate Restricted Generation";
                case BIOME_ALTITUDE -> "Biome Altitude";
                case INLAND_HEIGHT -> "Inland Height";
                default -> titleCaseId(serializedName);
            };
        }

        public List<LegendEntry> legend() {
            return switch (this) {
                case RIVERS_AND_MOUNTAINS -> List.of(
                        new LegendEntry(argb(22, 217, 236), "River"),
                        new LegendEntry(argb(244, 102, 57), "Coastal mountain"),
                        new LegendEntry(argb(224, 224, 212), "Inland mountain"),
                        new LegendEntry(argb(105, 112, 205), "Lake"),
                        new LegendEntry(argb(31, 126, 44), "Land")
                );
                case GEOLOGY -> List.of(
                        new LegendEntry(argb(151, 126, 74), "Continental sedimentary"),
                        new LegendEntry(argb(174, 66, 47), "Volcanic"),
                        new LegendEntry(argb(126, 148, 174), "Uplift"),
                        new LegendEntry(argb(43, 49, 59), "Mafic deep")
                );
                case ROCKS -> List.of(
                        new LegendEntry(rockColor("granite"), "Each color is a surface rock"),
                        new LegendEntry(rockColor("basalt"), "Click the map for its name")
                );
                case BIOMES -> List.of(
                        new LegendEntry(argb(49, 70, 184), "Ocean biomes"),
                        new LegendEntry(argb(79, 156, 76), "Land biomes"),
                        new LegendEntry(argb(221, 223, 226), "Cold biomes")
                );
                case RAINFALL -> List.of(
                        new LegendEntry(argb(203, 170, 94), "0 mm - dry"),
                        new LegendEntry(argb(83, 157, 91), "250 mm"),
                        new LegendEntry(argb(38, 88, 151), "500 mm - wet")
                );
                case TEMPERATURE -> List.of(
                        new LegendEntry(argb(38, 63, 138), "-20 C - cold"),
                        new LegendEntry(argb(171, 196, 91), "10 C"),
                        new LegendEntry(argb(181, 55, 43), "30 C - hot")
                );
                case CLIMATE_RESTRICTED_GENERATION -> List.of(
                        new LegendEntry(argb(218, 236, 244), "Frozen surface"),
                        new LegendEntry(argb(205, 166, 83), "Arid surface"),
                        new LegendEntry(argb(83, 154, 92), "Wetland surface"),
                        new LegendEntry(argb(154, 198, 109), "Karst terrain")
                );
                case BIOME_ALTITUDE -> List.of(
                        new LegendEntry(argb(19, 105, 32), "Low"),
                        new LegendEntry(argb(34, 139, 48), "Medium"),
                        new LegendEntry(argb(65, 178, 75), "High"),
                        new LegendEntry(argb(112, 216, 112), "Mountain")
                );
                case INLAND_HEIGHT -> List.of(
                        new LegendEntry(argb(22, 91, 31), "Land height: low"),
                        new LegendEntry(argb(76, 217, 85), "Land height: high"),
                        new LegendEntry(argb(71, 103, 195), "Shallow water"),
                        new LegendEntry(argb(43, 61, 153), "Deep water")
                );
                default -> List.of();
            };
        }

        public PointInfo inspect(EarthGeneratorState state, int blockX, int blockZ) {
            EarthRegion.Point point = state.regionGenerator().sampleSemantic(
                    EarthUnits.blockToGridExact(blockX),
                    EarthUnits.blockToGridExact(blockZ)
            );
            var profile = state.terrainProfiles().profileAtBlock(blockX, blockZ);
            var climate = state.climateModel().sample(blockX, blockZ);
            int surfaceY = state.heightFiller().sampleBlockHeight(blockX, blockZ);

            return switch (this) {
                case RIVERS_AND_MOUNTAINS -> {
                    RiverInfo river = state.riverSampler().sample(blockX, blockZ);
                    List<String> lines = new java.util.ArrayList<>();
                    lines.add("Terrain: " + titleCaseId(profile.name()));
                    lines.add("Surface Y: " + surfaceY);
                    lines.add("Mountain: " + (point.coastalMountain() ? "coastal" : point.mountain() ? "inland" : "no"));
                    if (river == null) {
                        lines.add("River: none");
                    } else {
                        lines.add(String.format(Locale.ROOT, "River distance: %.1f blocks", Math.sqrt(river.distanceSq())));
                        lines.add(String.format(Locale.ROOT, "River half-width: %.1f blocks", Math.sqrt(river.widthSq())));
                    }
                    yield new PointInfo(List.copyOf(lines));
                }
                case GEOLOGY -> new PointInfo(List.of(
                        "Rock type: " + titleCaseId(point.geologicalProvince().name()),
                        "Semantic profile: " + titleCaseId(point.terrainProfile().name())
                ));
                case ROCKS -> {
                    var stack = state.stoneLayerSampler().sampleStack(blockX, blockZ);
                    String surfaceRock = state.stoneLayerSampler().sampleRockId(blockX, surfaceY, blockZ, surfaceY);
                    yield new PointInfo(List.of(
                            "Surface rock: " + titleCaseId(surfaceRock),
                            "Rock stack: " + stack.rockIds().stream().map(EarthPreviewService::titleCaseId)
                                    .collect(java.util.stream.Collectors.joining(" -> "))
                    ));
                }
                case BIOMES -> new PointInfo(List.of(
                        "Biome: " + titleCaseId(resolveBiome(state, blockX, blockZ).location().getPath()),
                        "Terrain: " + titleCaseId(profile.name()),
                        "Surface Y: " + surfaceY
                ));
                case RAINFALL -> new PointInfo(List.of(
                        String.format(Locale.ROOT, "Annual rainfall: %.1f mm", climate.averageRainfall()),
                        rainfallBand(climate.averageRainfall())
                ));
                case TEMPERATURE -> new PointInfo(List.of(
                        String.format(Locale.ROOT, "Average temperature: %.2f C", climate.averageTemperature()),
                        temperatureBand(climate.averageTemperature())
                ));
                case CLIMATE_RESTRICTED_GENERATION -> {
                    ClimateRestriction restriction = climateRestriction(state, blockX, blockZ);
                    yield new PointInfo(List.of(
                            "Generation: " + restriction.name(),
                            String.format(Locale.ROOT, "Temperature: %.1f C", climate.averageTemperature()),
                            String.format(Locale.ROOT, "Rainfall: %.1f mm", climate.averageRainfall())
                    ));
                }
                case BIOME_ALTITUDE -> new PointInfo(List.of(
                        "Biome altitude: " + altitudeName(point),
                        "Regional value: " + point.biomeAltitude(),
                        "Surface Y: " + surfaceY
                ));
                case INLAND_HEIGHT -> new PointInfo(List.of(
                        point.land() ? "Land height: " + point.baseLandHeight()
                                : "Distance to land: " + point.distanceToLand(),
                        "Final surface Y: " + surfaceY,
                        "Land: " + (point.land() ? "yes" : "no")
                ));
                default -> new PointInfo(List.of(
                        "Layer: " + displayName(),
                        "Terrain: " + titleCaseId(profile.name()),
                        "Surface Y: " + surfaceY
                ));
            };
        }

        abstract int color(
                EarthGeneratorState state,
                int blockX,
                int blockZ,
                EarthRegion.Point point
        );
    }

    public record LegendEntry(int color, String label) {
    }

    public record PointInfo(List<String> lines) {
        public PointInfo {
            lines = List.copyOf(lines);
        }
    }

    private record ClimateRestriction(String name, int color) {
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> resolveBiome(
            EarthGeneratorState state,
            int blockX,
            int blockZ
    ) {
        var profile = state.terrainProfiles().profileAtBlock(blockX, blockZ);
        var climate = state.climateModel().sample(blockX, blockZ);
        return EarthBiomeSemantics.resolve(
                profile,
                climate.averageTemperature(),
                climate.averageRainfall(),
                state.riverSampler().sample(blockX, blockZ) != null,
                state.karstModel().isKarst(blockX, blockZ)
        );
    }

    private static ClimateRestriction climateRestriction(EarthGeneratorState state, int blockX, int blockZ) {
        var profile = state.terrainProfiles().profileAtBlock(blockX, blockZ);
        var climate = state.climateModel().sample(blockX, blockZ);
        float temperature = climate.averageTemperature();
        float rainfall = climate.averageRainfall();
        if (EarthBiomeSemantics.isOcean(profile)) {
            return new ClimateRestriction("Ocean", argb(57, 78, 170));
        }
        if (EarthBiomeSemantics.temperatureAtFreezing(temperature, rainfall)) {
            return new ClimateRestriction("Frozen surface", argb(218, 236, 244));
        }
        if (state.karstModel().isKarst(blockX, blockZ)) {
            return new ClimateRestriction("Karst terrain", argb(154, 198, 109));
        }
        if (rainfall < 60.0F) {
            return new ClimateRestriction("Arid surface", argb(205, 166, 83));
        }
        if (rainfall < 155.0F) {
            return new ClimateRestriction("Semi-arid surface", argb(184, 163, 91));
        }
        if (rainfall > 425.0F && temperature > 12.0F) {
            return new ClimateRestriction("Wetland surface", argb(83, 154, 92));
        }
        return new ClimateRestriction("Temperate surface", argb(52, 125, 62));
    }

    private static String altitudeName(EarthRegion.Point point) {
        if (!point.land()) {
            return point.distanceToLand() > 5 ? "Deep water" : "Shallow water";
        }
        return switch (Math.clamp(point.discreteBiomeAltitude(), 0, 3)) {
            case 0 -> "Low";
            case 1 -> "Medium";
            case 2 -> "High";
            default -> "Mountain";
        };
    }

    private static String rainfallBand(float rainfall) {
        if (rainfall < 60.0F) return "Band: arid";
        if (rainfall < 155.0F) return "Band: semi-arid";
        if (rainfall < 250.0F) return "Band: moderate";
        if (rainfall < 375.0F) return "Band: humid";
        return "Band: very wet";
    }

    private static String temperatureBand(float temperature) {
        if (temperature < -10.0F) return "Band: polar";
        if (temperature < 0.0F) return "Band: freezing";
        if (temperature < 10.0F) return "Band: cool";
        if (temperature < 20.0F) return "Band: temperate";
        return "Band: hot";
    }

    private static int rockColor(String rock) {
        return switch (rock) {
            case "granite" -> argb(142, 116, 112);
            case "diorite" -> argb(190, 190, 184);
            case "gabbro" -> argb(65, 68, 70);
            case "black_granite" -> argb(44, 43, 48);
            case "red_granite" -> argb(139, 75, 69);
            case "basalt" -> argb(55, 59, 63);
            case "andesite" -> argb(123, 124, 122);
            case "tuff" -> argb(100, 111, 96);
            case "rhyolite" -> argb(154, 105, 99);
            case "dacite" -> argb(137, 126, 123);
            case "komatiite" -> argb(83, 112, 83);
            case "kimberlite" -> argb(67, 103, 102);
            case "limestone" -> argb(171, 166, 139);
            case "shale" -> argb(85, 84, 75);
            case "claystone" -> argb(133, 91, 65);
            case "dolomite" -> argb(164, 157, 140);
            case "conglomerate" -> argb(121, 99, 73);
            case "chert" -> argb(69, 70, 67);
            case "chalk" -> argb(218, 216, 196);
            case "slate" -> argb(72, 79, 91);
            case "marble" -> argb(213, 211, 202);
            case "quartzite" -> argb(179, 160, 165);
            case "gneiss" -> argb(124, 113, 104);
            case "phyllite" -> argb(105, 94, 105);
            case "green_schist" -> argb(82, 112, 86);
            case "blue_schist" -> argb(70, 88, 119);
            default -> {
                int hash = rock.hashCode();
                yield argb(64 + Math.floorMod(hash, 160),
                        64 + Math.floorMod(hash >> 8, 160),
                        64 + Math.floorMod(hash >> 16, 160));
            }
        };
    }

    private static String titleCaseId(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (capitalize && Character.isLetter(character)) {
                result.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                result.append(character);
            }
            if (character == ' ') {
                capitalize = true;
            }
        }
        return result.toString();
    }

    private static int climateGradient(float value, float[] stops, int[] colors) {
        if (stops.length != colors.length || stops.length == 0) {
            throw new IllegalArgumentException("Climate gradient stops and colors must have equal non-zero length");
        }
        if (value <= stops[0]) {
            return colors[0];
        }
        for (int index = 1; index < stops.length; index++) {
            if (value <= stops[index]) {
                float delta = (value - stops[index - 1]) / (stops[index] - stops[index - 1]);
                return lerpColor(colors[index - 1], colors[index], delta);
            }
        }
        return colors[colors.length - 1];
    }

    private static int lerpColor(int start, int end, float delta) {
        int red = Math.round(((start >> 16) & 0xFF) + delta * (((end >> 16) & 0xFF) - ((start >> 16) & 0xFF)));
        int green = Math.round(((start >> 8) & 0xFF) + delta * (((end >> 8) & 0xFF) - ((start >> 8) & 0xFF)));
        int blue = Math.round((start & 0xFF) + delta * ((end & 0xFF) - (start & 0xFF)));
        return argb(red, green, blue);
    }
}
