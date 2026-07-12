package gregapi.data;

import gregapi.code.MineralResource;
import gregapi.code.OreDropForm;
import gregapi.code.OreBlockForm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API-level mapping from ore/mineral resources to the item form they drop.
 *
 * <p>Most ores drop raw material. The exact raw quality is selected from the
 * ore block form: poor ore -> poor raw, normal ore -> raw, rich ore -> rich raw.
 * Gem-like ores can instead drop crystals.</p>
 */
public final class OreDropList {
    public static final Map<MineralResource, OreDropForm> ORE_DROPS;
    public static final List<MineralResource> CRYSTAL_ORES;
    public static final Map<OreBlockForm, List<OreDropChoice>> RAW_ORE_BLOCK_DROPS = Map.of(
            OreBlockForm.POOR, List.of(
                    new OreDropChoice(80, List.of(OreDropForm.POOR_RAW_MATERIAL)),
                    new OreDropChoice(20, List.of(OreDropForm.RAW_MATERIAL))
            ),
            OreBlockForm.NORMAL, List.of(
                    new OreDropChoice(85, List.of(OreDropForm.RAW_MATERIAL)),
                    new OreDropChoice(5, List.of(OreDropForm.RICH_RAW_MATERIAL)),
                    new OreDropChoice(5, List.of(OreDropForm.RAW_MATERIAL, OreDropForm.POOR_RAW_MATERIAL)),
                    new OreDropChoice(5, List.of(OreDropForm.POOR_RAW_MATERIAL))
            ),
            OreBlockForm.RICH, List.of(
                    new OreDropChoice(60, List.of(OreDropForm.RICH_RAW_MATERIAL)),
                    new OreDropChoice(20, List.of(OreDropForm.RAW_MATERIAL)),
                    new OreDropChoice(20, List.of(OreDropForm.RAW_MATERIAL, OreDropForm.POOR_RAW_MATERIAL))
            )
    );

    static {
        Map<MineralResource, OreDropForm> drops = new LinkedHashMap<>();
        List<MineralResource> crystalOres = new ArrayList<>();
        Map<String, OreDropForm> specialDrops = Map.ofEntries(
                crystal("amethyst"),
                crystal("diamond"),
                crystal("emerald"),
                crystal("ruby"),
                crystal("sapphire"),
                crystal("green_sapphire"),
                crystal("blue_topaz"),
                crystal("topaz"),
                crystal("opal"),
                crystal("olivine"),
                crystal("lazurite"),
                crystal("sodalite")
        );

        for (MineralResource ore : NaturalResourceList.ORES) {
            OreDropForm form = specialDrops.getOrDefault(ore.id(), OreDropForm.RAW_MATERIAL);
            drops.put(ore, form);

            if (form == OreDropForm.CRYSTAL) {
                crystalOres.add(ore);
            }
        }

        ORE_DROPS = Map.copyOf(drops);
        CRYSTAL_ORES = List.copyOf(crystalOres);
    }

    private OreDropList() {
    }

    private static Map.Entry<String, OreDropForm> crystal(String oreId) {
        return Map.entry(oreId, OreDropForm.CRYSTAL);
    }

    public static OreDropForm dropForm(MineralResource ore) {
        return ORE_DROPS.getOrDefault(ore, OreDropForm.RAW_MATERIAL);
    }

    public static List<OreDropChoice> rawDropChoicesForOreBlockForm(OreBlockForm blockForm) {
        return RAW_ORE_BLOCK_DROPS.getOrDefault(blockForm, List.of(
                new OreDropChoice(100, List.of(OreDropForm.RAW_MATERIAL))
        ));
    }

    public record OreDropChoice(int weight, List<OreDropForm> drops) {
        public OreDropChoice {
            if (weight <= 0) {
                throw new IllegalArgumentException("Ore drop choice weight must be positive");
            }
            drops = List.copyOf(drops);
            if (drops.isEmpty()) {
                throw new IllegalArgumentException("Ore drop choice cannot be empty");
            }
        }
    }
}
