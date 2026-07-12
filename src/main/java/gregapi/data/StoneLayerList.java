package gregapi.data;

import gregapi.worldgen.GeologicalRules.RegionType;

import gregapi.worldgen.GeologicalRules.RockGroup;

import gregapi.code.BlockTypeDefinitions;
import gregapi.worldgen.StoneLayerRules.StoneLayerDefinition;
import gregapi.worldgen.StoneLayerRules.StoneLayerStackDefinition;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API-level GT-style stone layer table for Overworld generation.
 *
 * <p>Stone layer frequency is controlled by {@link #OVERWORLD_LAYER_STACKS}.
 * Individual layer definitions only classify available rocks.</p>
 */
public final class StoneLayerList {
    public static final List<StoneLayerDefinition> OVERWORLD_LAYERS = List.of(
            layer("granite", RockGroup.IGNEOUS_INTRUSIVE),
            layer("diorite", RockGroup.IGNEOUS_INTRUSIVE),
            layer("gabbro", RockGroup.IGNEOUS_INTRUSIVE),
            layer("black_granite", RockGroup.IGNEOUS_INTRUSIVE),
            layer("red_granite", RockGroup.IGNEOUS_INTRUSIVE),

            layer("basalt", RockGroup.IGNEOUS_EXTRUSIVE),
            layer("andesite", RockGroup.IGNEOUS_EXTRUSIVE),
            layer("tuff", RockGroup.IGNEOUS_EXTRUSIVE),
            layer("rhyolite", RockGroup.IGNEOUS_EXTRUSIVE),
            layer("dacite", RockGroup.IGNEOUS_EXTRUSIVE),
            layer("komatiite", RockGroup.IGNEOUS_EXTRUSIVE),
            layer("kimberlite", RockGroup.SPECIAL),

            layer("limestone", RockGroup.SEDIMENTARY),
            layer("shale", RockGroup.SEDIMENTARY),
            layer("claystone", RockGroup.SEDIMENTARY),
            layer("dolomite", RockGroup.SEDIMENTARY),
            layer("conglomerate", RockGroup.SEDIMENTARY),
            layer("chert", RockGroup.SEDIMENTARY),
            layer("chalk", RockGroup.SEDIMENTARY),

            layer("slate", RockGroup.METAMORPHIC),
            layer("marble", RockGroup.METAMORPHIC),
            layer("quartzite", RockGroup.METAMORPHIC),
            layer("gneiss", RockGroup.METAMORPHIC),
            layer("phyllite", RockGroup.METAMORPHIC),
            layer("green_schist", RockGroup.METAMORPHIC),
            layer("blue_schist", RockGroup.METAMORPHIC)
    );

    /*
     * Stack selection weights inside each geological stack category.
     *
     * The second stack(...) argument is the stack weight. All stacks currently
     * have weight 1, so each stack has an equal chance inside its category:
     *
     * CONTINENTAL_SEDIMENTARY: 8 stacks, each 1/8 = 12.50%
     *   limestone -> marble -> gneiss
     *   dolomite -> marble -> gneiss
     *   chalk -> limestone -> marble
     *   chert -> quartzite -> granite
     *   shale -> slate -> phyllite -> gneiss
     *   claystone -> slate -> phyllite -> gneiss
     *   conglomerate -> shale -> slate -> phyllite
     *   limestone -> dolomite -> marble
     *
     * VOLCANIC: 8 stacks, each 1/8 = 12.50%
     *   basalt -> gabbro
     *   andesite -> diorite
     *   dacite -> granite
     *   rhyolite -> granite
     *   rhyolite -> red_granite
     *   tuff -> andesite -> diorite
     *   tuff -> basalt -> gabbro
     *   dacite -> diorite -> granite
     *
     * UPLIFT: 8 stacks, each 1/8 = 12.50%
     *   slate -> phyllite -> green_schist -> gneiss
     *   phyllite -> green_schist -> gneiss
     *   green_schist -> gneiss
     *   blue_schist -> green_schist -> gneiss
     *   quartzite -> gneiss
     *   marble -> gneiss
     *   slate -> phyllite -> gneiss
     *   quartzite -> granite
     *
     * MAFIC_DEEP: 8 stacks, each 1/8 = 12.50%
     *   basalt -> gabbro
     *   gabbro -> black_granite
     *   komatiite -> basalt -> gabbro
     *   komatiite -> gabbro
     *   kimberlite -> gabbro
     *   black_granite -> gabbro
     *   basalt -> black_granite
     *   komatiite -> black_granite
     *
     * Rock frequency inside each geological stack category.
     *
     * Formula:
     *   rock mentions inside category / total rock mentions inside category.
     *
     * Current stack weights are all 1, so these values count raw rock mentions
     * in the stack definitions below. Recalculate this comment if stack weights
     * or stack contents change.
     *
     * CONTINENTAL_SEDIMENTARY total = 27:
     *   gneiss        4/27 = 14.81%
     *   marble        4/27 = 14.81%
     *   limestone     3/27 = 11.11%
     *   phyllite      3/27 = 11.11%
     *   slate         3/27 = 11.11%
     *   dolomite      2/27 =  7.41%
     *   shale         2/27 =  7.41%
     *   chalk         1/27 =  3.70%
     *   chert         1/27 =  3.70%
     *   claystone     1/27 =  3.70%
     *   conglomerate  1/27 =  3.70%
     *   granite       1/27 =  3.70%
     *   quartzite     1/27 =  3.70%
     *
     * VOLCANIC total = 19:
     *   diorite       3/19 = 15.79%
     *   granite       3/19 = 15.79%
     *   andesite      2/19 = 10.53%
     *   basalt        2/19 = 10.53%
     *   dacite        2/19 = 10.53%
     *   gabbro        2/19 = 10.53%
     *   rhyolite      2/19 = 10.53%
     *   tuff          2/19 = 10.53%
     *   red_granite   1/19 =  5.26%
     *
     * UPLIFT total = 21:
     *   gneiss        7/21 = 33.33%
     *   green_schist  4/21 = 19.05%
     *   phyllite      3/21 = 14.29%
     *   quartzite     2/21 =  9.52%
     *   slate         2/21 =  9.52%
     *   blue_schist   1/21 =  4.76%
     *   granite       1/21 =  4.76%
     *   marble        1/21 =  4.76%
     *
     * MAFIC_DEEP total = 17:
     *   gabbro        6/17 = 35.29%
     *   black_granite 4/17 = 23.53%
     *   basalt        3/17 = 17.65%
     *   komatiite     3/17 = 17.65%
     *   kimberlite    1/17 =  5.88%
     */
    public static final List<StoneLayerStackDefinition> OVERWORLD_LAYER_STACKS = List.of(
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "limestone", "marble", "gneiss"),
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "dolomite", "marble", "gneiss"),
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "chalk", "limestone", "marble"),
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "chert", "quartzite", "granite"),
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "shale", "slate", "phyllite", "gneiss"),
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "claystone", "slate", "phyllite", "gneiss"),
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "conglomerate", "shale", "slate", "phyllite"),
            stack(RegionType.CONTINENTAL_SEDIMENTARY, 1, "limestone", "dolomite", "marble"),

            stack(RegionType.VOLCANIC, 1, "basalt", "gabbro"),
            stack(RegionType.VOLCANIC, 1, "andesite", "diorite"),
            stack(RegionType.VOLCANIC, 1, "dacite", "granite"),
            stack(RegionType.VOLCANIC, 1, "rhyolite", "granite"),
            stack(RegionType.VOLCANIC, 1, "rhyolite", "red_granite"),
            stack(RegionType.VOLCANIC, 1, "tuff", "andesite", "diorite"),
            stack(RegionType.VOLCANIC, 1, "tuff", "basalt", "gabbro"),
            stack(RegionType.VOLCANIC, 1, "dacite", "diorite", "granite"),

            stack(RegionType.UPLIFT, 1, "slate", "phyllite", "green_schist", "gneiss"),
            stack(RegionType.UPLIFT, 1, "phyllite", "green_schist", "gneiss"),
            stack(RegionType.UPLIFT, 1, "green_schist", "gneiss"),
            stack(RegionType.UPLIFT, 1, "blue_schist", "green_schist", "gneiss"),
            stack(RegionType.UPLIFT, 1, "quartzite", "gneiss"),
            stack(RegionType.UPLIFT, 1, "marble", "gneiss"),
            stack(RegionType.UPLIFT, 1, "slate", "phyllite", "gneiss"),
            stack(RegionType.UPLIFT, 1, "quartzite", "granite"),

            stack(RegionType.MAFIC_DEEP, 1, "basalt", "gabbro"),
            stack(RegionType.MAFIC_DEEP, 1, "gabbro", "black_granite"),
            stack(RegionType.MAFIC_DEEP, 1, "komatiite", "basalt", "gabbro"),
            stack(RegionType.MAFIC_DEEP, 1, "komatiite", "gabbro"),
            stack(RegionType.MAFIC_DEEP, 1, "kimberlite", "gabbro"),
            stack(RegionType.MAFIC_DEEP, 1, "black_granite", "gabbro"),
            stack(RegionType.MAFIC_DEEP, 1, "basalt", "black_granite"),
            stack(RegionType.MAFIC_DEEP, 1, "komatiite", "black_granite")
    );

    public static final Map<String, StoneLayerDefinition> BY_ID;

    static {
        validateAgainstRockTypes();

        Map<String, StoneLayerDefinition> byId = new LinkedHashMap<>();
        for (StoneLayerDefinition layer : OVERWORLD_LAYERS) {
            StoneLayerDefinition previous = byId.putIfAbsent(layer.rockId(), layer);

            if (previous != null) {
                throw new IllegalStateException("Duplicate stone layer rock id: " + layer.rockId());
            }
        }
        BY_ID = Collections.unmodifiableMap(byId);

        validateRegionsAndStacks();
    }

    private StoneLayerList() {
    }

    public static Optional<StoneLayerDefinition> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    public static List<StoneLayerDefinition> byGroup(RockGroup group) {
        if (group == null) {
            return List.of();
        }
        return OVERWORLD_LAYERS.stream()
                .filter(layer -> layer.group() == group)
                .toList();
    }

    private static StoneLayerDefinition layer(String rockId, RockGroup group) {
        return new StoneLayerDefinition(rockId, group);
    }

    private static StoneLayerStackDefinition stack(RegionType type, int weight, String... rockIds) {
        return new StoneLayerStackDefinition(type, weight, List.of(rockIds));
    }

    private static void validateAgainstRockTypes() {
        Set<String> rockIds = RocksList.ROCKS.stream()
                .map(BlockTypeDefinitions.RockType::id)
                .collect(Collectors.toUnmodifiableSet());

        for (StoneLayerDefinition layer : OVERWORLD_LAYERS) {
            if (!rockIds.contains(layer.rockId())) {
                throw new IllegalStateException("Stone layer references missing rock type: " + layer.rockId());
            }
            if ("stone".equals(layer.rockId())) {
                throw new IllegalStateException("gt6uou:stone must not be used as a generated stone layer");
            }
        }

        Set<String> layerIds = OVERWORLD_LAYERS.stream()
                .map(StoneLayerDefinition::rockId)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> generatedRockIds = rockIds.stream()
                .filter(id -> !"stone".equals(id))
                .collect(Collectors.toUnmodifiableSet());

        Set<String> missingLayers = generatedRockIds.stream()
                .filter(id -> !layerIds.contains(id))
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        if (!missingLayers.isEmpty()) {
            throw new IllegalStateException("Rock types missing from Overworld stone layers: " + missingLayers);
        }
    }

    private static void validateRegionsAndStacks() {
        Set<String> layerIds = BY_ID.keySet();
        Set<String> usedRockIds = new java.util.TreeSet<>();

        for (StoneLayerStackDefinition stack : OVERWORLD_LAYER_STACKS) {
            Set<String> stackRockIds = new java.util.HashSet<>();

            for (String rockId : stack.rockIds()) {
                validateKnownLayerRock(rockId, "Stone layer stack " + stack.type() + " references missing rock: ");
                usedRockIds.add(rockId);

                if (!stackRockIds.add(rockId)) {
                    throw new IllegalStateException("Stone layer stack contains duplicate rock id " + rockId + ": " + stack.rockIds());
                }
            }
        }

        Set<String> unusedRockIds = layerIds.stream()
                .filter(id -> !usedRockIds.contains(id))
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        if (!unusedRockIds.isEmpty()) {
            throw new IllegalStateException("Overworld stone layer rocks are not used by any region/transition: " + unusedRockIds);
        }
    }

    private static void validateKnownLayerRock(String rockId, String messagePrefix) {
        if (!BY_ID.containsKey(rockId)) {
            throw new IllegalStateException(messagePrefix + rockId);
        }
    }
}
