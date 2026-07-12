package gregapi.data;

import gregapi.worldgen.OreVeinRules.OreVeinDefinition;
import gregapi.worldgen.OreVeinRules.VeinDimensions;
import gregapi.worldgen.OreVeinRules.OreVeinOreEntry;
import gregapi.worldgen.OreVeinRules.OreVeinOreRole;
import gregapi.worldgen.OreVeinRules.OreVeinShapeDefinition;
import gregapi.worldgen.OreVeinRules.OreVeinShapeSettings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static gregapi.worldgen.OreVeinRules.OreVeinOreEntry.dike;
import static gregapi.worldgen.OreVeinRules.OreVeinOreEntry.layer;
import static gregapi.worldgen.OreVeinRules.OreVeinOreEntry.ore;
import static gregapi.worldgen.OreVeinRules.OreVeinOreEntry.rare;
import static gregapi.worldgen.OreVeinRules.OreVeinOreEntry.role;

/**
 * GTM default ore vein catalog.
 *
 * <p>Source: {@code GregTech-Modern-1.20.1/src/main/java/com/gregtechceu/gtceu/common/data/GTOres.java}.
 * This mirrors GTM vein ids, dimensions, source layers, height ranges, cluster
 * sizes, density, generator shape and material composition. The port selection
 * weight is intentionally equal for every vein ({@code selectionWeight = 1});
 * GTM's original {@code weight(...)} is kept only as {@code sourceGtmWeight}
 * for reference.</p>
 *
 * <p>Placement in this port is rock-hosted rather than height-hosted. GTM
 * Overworld {@code STONE} and {@code DEEPSLATE} veins are attached to
 * compatible GT6UOU stone layer rock ids.</p>
 */
public final class VeinList {
    public static final List<OreVeinDefinition> END = List.of(
            vein("bauxite_vein_end", "Bauxite Vein", VeinDimensions.END, "endstone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 40, 10, 80), List.of("end_stone"),
                    layer("bauxite", 2, 1, 4), layer("ilmenite", 1, 1, 2), layer("alunite", 1, 1, 1)),
            vein("magnetite_vein_end", "Magnetite Vein (The End)", VeinDimensions.END, "endstone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 30, 20, 80), List.of("end_stone"),
                    layer("magnetite", 3, 1, 4), layer("vanadium_magnetite", 2, 1, 2), layer("chromite", 2, 1, 1), layer("gold", 1, 1, 1)),
            vein("pitchblende_vein_end", "Pitchblende Vein", VeinDimensions.END, "endstone", OreVeinShapeList.CUBOID,
                    settings(34, 36, 0.305f, 30, 30, 60), List.of("end_stone"),
                    role("pitchblende", OreVeinOreRole.TOP, 2), role("pitchblende", OreVeinOreRole.MIDDLE, 3),
                    role("pitchblende", OreVeinOreRole.BOTTOM, 2), role("uraninite", OreVeinOreRole.SPREAD, 1)),
            vein("scheelite_vein", "Scheelite Vein", VeinDimensions.END, "endstone", OreVeinShapeList.DIKE,
                    settings(34, 36, 0.75f, 20, 20, 60), List.of("end_stone"),
                    dike("scheelite", 3, 20, 60), dike("tungstate", 2, 35, 55), dike("spodumene", 1, 20, 40)),
            vein("sheldonite_vein", "Sheldonite Vein", VeinDimensions.END, "endstone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 10, 5, 50), List.of("end_stone"),
                    layer("bornite", 3, 2, 4), layer("cooperite", 2, 1, 1), layer("platinum", 2, 1, 1), layer("palladium", 1, 1, 1))
    );

    public static final List<OreVeinDefinition> NETHER = List.of(
            vein("banded_iron_vein", "Banded Iron Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.MIXED_CLUSTER,
                    veined(27, 30, 0.70f, 30, 20, 40, 0.075f), List.of("netherrack"),
                    ore("goethite", 3), ore("limonite", 2), ore("hematite", 2), rare("gold", 1)),
            vein("beryllium_vein", "Beryllium Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.DIKE,
                    settings(34, 36, 0.75f, 30, 5, 30), List.of("netherrack"),
                    dike("emerald", 3, 5, 30), dike("emerald", 2, 5, 19), dike("monazite", 1, 16, 30)),
            vein("manganese_vein", "Manganese Vein (Nether)", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.DIKE,
                    settings(34, 36, 0.75f, 20, 20, 30), List.of("netherrack"),
                    dike("grossular", 3, 20, 30), dike("pyrolusite", 2, 20, 26), dike("tantalite", 1, 24, 30)),
            vein("molybdenum_vein", "Molybdenum Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 5, 20, 50), List.of("netherrack"),
                    layer("wulfenite", 3, 2, 4), layer("molybdenite", 2, 1, 1), layer("molybdenite", 1, 1, 1), layer("powellite", 1, 1, 1)),
            vein("monazite_vein", "Monazite Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 30, 20, 40), List.of("netherrack"),
                    layer("bastnasite", 3, 2, 4), layer("monazite", 1, 1, 1), layer("monazite", 1, 1, 1)),
            vein("nether_redstone_vein", "Redstone Vein (Nether)", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 60, 5, 40), List.of("netherrack"),
                    layer("redstone", 3, 2, 4), layer("ruby", 2, 1, 1), layer("cinnabar", 1, 1, 1)),
            vein("saltpeter_vein", "Saltpeter Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 40, 5, 45), List.of("netherrack"),
                    layer("saltpeter", 3, 2, 4), layer("diatomite", 2, 1, 1), layer("electrotine", 2, 1, 1), layer("alunite", 1, 1, 1)),
            vein("sulfur_vein", "Sulfur Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 100, 10, 30), List.of("netherrack"),
                    layer("sulfur", 3, 2, 4), layer("pyrite", 2, 1, 1), layer("sphalerite", 1, 1, 1)),
            vein("tetrahedrite_vein", "Tetrahedryte Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.MIXED_CLUSTER,
                    veined(27, 30, 0.70f, 70, 80, 120, 0.15f), List.of("netherrack"),
                    ore("tetrahedrite", 4), ore("copper", 2), rare("stibnite", 1)),
            vein("topaz_vein", "Topaz Vein", VeinDimensions.NETHER, "netherrack", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 70, 80, 120), List.of("netherrack"),
                    layer("blue_topaz", 3, 2, 4), layer("topaz", 2, 1, 1), layer("chalcocite", 2, 1, 1), layer("bornite", 1, 1, 1))
    );

    public static final List<OreVeinDefinition> OVERWORLD = List.of(
            vein("apatite_vein", "Apatite Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 40, 10, 80), host("granite", "red_granite", "rhyolite", "dacite", "limestone", "dolomite", "chalk", "chert", "marble", "tuff"),
                    layer("apatite", 3, 2, 4), layer("tricalcium_phosphate", 2, 1, 1), layer("pyrochlore", 1, 1, 1)),
            vein("cassiterite_vein", "Cassiterite Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.MIXED_CLUSTER,
                    veined(27, 30, 0.70f, 80, 10, 80, 0.33f), host("granite", "red_granite", "rhyolite", "gneiss", "quartzite"),
                    ore("tin", 4), rare("cassiterite", 2)),
            vein("coal_vein", "Coal Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 80, 10, 140), host("shale", "claystone"),
                    layer("coal", 3, 2, 4)),
            vein("copper_tin_vein", "Copper Tin Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.MIXED_CLUSTER,
                    veined(27, 30, 0.70f, 50, -10, 160, 0.1f), host("granite", "red_granite", "diorite", "dacite"),
                    ore("chalcopyrite", 5), ore("zeolite", 2), ore("cassiterite", 2), rare("realgar", 1)),
            vein("galena_vein", "Galena Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 40, -15, 45), host("limestone", "dolomite", "chalk", "chert", "shale", "marble", "diorite", "andesite", "dacite"),
                    layer("galena", 3, 2, 4), layer("silver", 2, 1, 1), layer("lead", 1, 1, 1)),
            vein("garnet_tin_vein", "Garnet Tin Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 80, 30, 60), host("conglomerate"),
                    layer("cassiterite_sand", 3, 2, 4), layer("garnet_sand", 2, 1, 1), layer("asbestos", 2, 1, 1), layer("diatomite", 1, 1, 1)),
            vein("garnet_vein", "Garnet Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.DIKE,
                    settings(34, 36, 0.75f, 40, -10, 50), host("slate", "phyllite", "green_schist", "gneiss", "kimberlite"),
                    dike("red_garnet", 3, -10, 50), dike("yellow_garnet", 2, -10, 50), dike("amethyst", 2, -10, 22), dike("opal", 1, 18, 50)),
            vein("iron_vein", "Iron Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.MIXED_CLUSTER,
                    veined(27, 30, 0.70f, 120, -10, 60, null), host("diorite", "andesite", "claystone", "conglomerate"),
                    ore("goethite", 5), ore("limonite", 2), ore("hematite", 2), ore("malachite", 1)),
            vein("lubricant_vein", "Lubricant Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 40, 0, 50), host("limestone"),
                    layer("soapstone", 3, 2, 4), layer("talc", 2, 1, 1), layer("glauconite_sand", 2, 1, 1), layer("pentlandite", 1, 1, 1)),
            vein("magnetite_vein_ow", "Magnetite Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 80, 10, 60), host("black_granite", "diorite", "gabbro", "basalt", "andesite", "komatiite"),
                    layer("magnetite", 3, 2, 4), layer("vanadium_magnetite", 2, 1, 1), layer("gold", 1, 1, 1)),
            vein("mineral_sand_vein", "Mineral Sand Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 80, 15, 60), host("basalt", "tuff", "conglomerate"),
                    layer("basaltic_mineral_sand", 3, 2, 4), layer("granitic_mineral_sand", 2, 1, 1), layer("fullers_earth", 2, 1, 1), layer("gypsum", 1, 1, 1)),
            vein("nickel_vein", "Nickel Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 40, -10, 60), host("black_granite", "gabbro", "basalt", "komatiite", "green_schist"),
                    layer("garnierite", 3, 2, 4), layer("nickel", 2, 1, 1), layer("cobaltite", 2, 1, 1), layer("pentlandite", 1, 1, 1)),
            vein("salts_vein", "Salts Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 50, 30, 70), host("limestone", "dolomite", "chalk", "shale", "claystone"),
                    layer("halite", 3, 2, 4), layer("sylvite", 2, 1, 1), layer("lepidolite", 1, 1, 1), layer("spodumene", 1, 1, 1)),
            vein("oilsands_vein", "Oilsands Vein", VeinDimensions.OVERWORLD, "stone", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 40, 30, 80), host("shale", "claystone"),
                    layer("oilsands", 3, 2, 4), layer("oilsands", 2, 1, 1), layer("oilsands", 1, 1, 1), layer("oilsands", 1, 1, 1)),
            vein("copper_vein", "Copper Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.MIXED_CLUSTER,
                    veined(27, 30, 0.70f, 80, -40, 10, null), host("gabbro", "andesite", "tuff", "conglomerate", "green_schist"),
                    ore("chalcopyrite", 5), ore("iron", 2), ore("pyrite", 2), ore("copper", 2)),
            vein("diamond_vein", "Diamond Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.CLASSIC_BANDED,
                    settings(33, 35, 0.50f, 40, -55, -30), host("kimberlite"),
                    role("graphite", OreVeinOreRole.PRIMARY, 4), role("graphite", OreVeinOreRole.SECONDARY, 3),
                    role("diamond", OreVeinOreRole.BETWEEN, 3), role("coal", OreVeinOreRole.SPORADIC, 1)),
            vein("lapis_vein", "Lapis Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.DIKE,
                    settings(34, 36, 0.75f, 40, -60, 10), host("chert", "slate", "blue_schist", "marble", "quartzite"),
                    dike("lazurite", 3, -60, 10), dike("sodalite", 2, -50, 0), dike("lapis", 2, -50, 0), dike("calcite", 1, -40, 10)),
            vein("manganese_vein_ow", "Manganese Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.DIKE,
                    settings(34, 36, 0.75f, 20, -30, 0), host("dolomite", "slate", "phyllite", "blue_schist", "marble"),
                    dike("grossular", 3, -50, -5), dike("spessartine", 2, -40, -15), dike("pyrolusite", 2, -40, -15), dike("tantalite", 1, -30, -5)),
            vein("mica_vein", "Mica Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 20, -40, -10), host("granite", "rhyolite", "dacite", "gneiss", "phyllite"),
                    layer("kyanite", 3, 2, 4), layer("mica", 2, 1, 1), layer("bauxite", 2, 1, 1), layer("pollucite", 1, 1, 1)),
            vein("olivine_vein", "Olivine Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 20, -20, 10), host("black_granite", "gabbro", "basalt", "komatiite"),
                    layer("bentonite", 3, 2, 4), layer("magnesite", 2, 1, 1), layer("olivine", 2, 1, 1), layer("glauconite_sand", 1, 1, 1)),
            vein("redstone_vein_ow", "Redstone Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 60, -65, -10), host("red_granite", "tuff", "rhyolite", "chert", "quartzite"),
                    layer("redstone", 3, 2, 4), layer("ruby", 2, 1, 1), layer("cinnabar", 1, 1, 1)),
            vein("sapphire_vein", "Sapphire Vein", VeinDimensions.OVERWORLD, "deepslate", OreVeinShapeList.LAYERED_HORIZONTAL,
                    settings(40, 44, 0.60f, 60, -40, 0), host("slate", "phyllite", "green_schist", "blue_schist", "gneiss", "quartzite"),
                    layer("almandine", 3, 2, 4), layer("pyrope", 2, 1, 1), layer("sapphire", 1, 1, 1), layer("green_sapphire", 1, 1, 1))
    );

    public static final List<OreVeinDefinition> ALL = List.of(OVERWORLD, NETHER, END)
            .stream()
            .flatMap(List::stream)
            .toList();

    public static final Map<String, OreVeinDefinition> BY_ID;
    public static final Set<String> USED_ORE_IDS;
    public static final Set<String> UNUSED_ORE_IDS;
    public static final Set<String> UNKNOWN_ORE_IDS;

    static {
        Map<String, OreVeinDefinition> byId = new LinkedHashMap<>();
        Set<String> usedOreIds = new LinkedHashSet<>();

        for (OreVeinDefinition vein : ALL) {
            OreVeinDefinition previous = byId.putIfAbsent(vein.id(), vein);
            if (previous != null) {
                throw new IllegalStateException("Duplicate GTM ore vein id: " + vein.id());
            }
            validateHostRocks(vein);
            usedOreIds.addAll(vein.oreIds());
        }

        Set<String> knownResourceIds = NaturalResourceList.ALL.stream()
                .map(resource -> resource.id().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> knownOreIds = NaturalResourceList.ORES.stream()
                .map(resource -> resource.id().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> unusedOreIds = new LinkedHashSet<>();
        knownOreIds.stream()
                .filter(id -> !usedOreIds.contains(id))
                .forEach(unusedOreIds::add);

        Set<String> unknownOreIds = new LinkedHashSet<>();
        usedOreIds.stream()
                .filter(id -> !knownResourceIds.contains(id))
                .forEach(unknownOreIds::add);

        BY_ID = Collections.unmodifiableMap(byId);
        USED_ORE_IDS = Collections.unmodifiableSet(usedOreIds);
        UNUSED_ORE_IDS = Collections.unmodifiableSet(unusedOreIds);
        UNKNOWN_ORE_IDS = Collections.unmodifiableSet(unknownOreIds);
    }

    private VeinList() {
    }

    public static Optional<OreVeinDefinition> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    private static OreVeinDefinition vein(
            String id,
            String englishName,
            VeinDimensions dimension,
            String sourceLayerId,
            OreVeinShapeDefinition shape,
            OreVeinShapeSettings settings,
            List<String> hostRockIds,
            OreVeinOreEntry... entries
    ) {
        return new OreVeinDefinition(id, englishName, dimension, sourceLayerId, shape, settings, hostRockIds, List.of(entries));
    }

    private static OreVeinShapeSettings settings(int clusterMin, int clusterMax, float density, int sourceGtmWeight, int sourceMinY, int sourceMaxY) {
        return OreVeinShapeSettings.basic(clusterMin, clusterMax, density, sourceGtmWeight, sourceMinY, sourceMaxY);
    }

    private static OreVeinShapeSettings veined(
            int clusterMin,
            int clusterMax,
            float density,
            int sourceGtmWeight,
            int sourceMinY,
            int sourceMaxY,
            Float rareBlockChance
    ) {
        return settings(clusterMin, clusterMax, density, sourceGtmWeight, sourceMinY, sourceMaxY)
                .withVeinedNoise(rareBlockChance, 0.01f, 0.175f, 0.7f, 1.0f, 3, 0.1f);
    }

    private static List<String> host(String... rockIds) {
        return List.of(rockIds);
    }

    private static void validateHostRocks(OreVeinDefinition vein) {
        if (vein.hostRockIds().isEmpty()) {
            throw new IllegalStateException("GTM ore vein has no host rocks: " + vein.id());
        }
        if (vein.dimension() != VeinDimensions.OVERWORLD) {
            return;
        }
        for (String rockId : vein.hostRockIds()) {
            if (StoneLayerList.byId(rockId).isEmpty()) {
                throw new IllegalStateException("GTM Overworld ore vein " + vein.id() + " references missing host rock: " + rockId);
            }
        }
    }
}
