package gregapi.data;

import gregapi.code.MineralResource;
import gregapi.tools.ScannerList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * API-level list of useful natural resources.
 *
 * <p>This is a scanner/prospecting-facing classification layer. It answers
 * "is this natural block interesting as a resource?" without deciding how the
 * block is generated, processed, or registered.</p>
 */
public final class NaturalResourceList {
    public static final List<MineralResource> DEPOSITS = Stream.concat(
            RocksList.CLAYS.stream()
                    .map(clay -> resource(clay.id(), clay.englishName(), ScannerList.DEPOSIT)),
            RocksList.SANDS.stream()
                    .filter(sand -> sand.id().equals("oilsands")
                            || sand.id().equals("black_sand")
                            || sand.id().equals("basaltic_black_sand")
                            || sand.id().equals("granitic_black_sand")
                            || sand.id().equals("basaltic_mineral_sand")
                            || sand.id().equals("granitic_mineral_sand")
                            || sand.id().equals("cassiterite_sand")
                            || sand.id().equals("garnet_sand")
                            || sand.id().equals("glauconite_sand"))
                    .map(sand -> resource(sand.id(), sand.englishName(), ScannerList.DEPOSIT))
    ).toList();

    /**
     * Ore/mineral material ids collected from GTM ore generation data.
     *
     * <p>{@code ???} means the material is fictional/mod-specific or the checked
     * source did not expose a real chemical formula. Keep those unknowns explicit
     * instead of silently inventing fake chemistry.</p>
     */
    public static final List<MineralResource> ORES = List.of(
            ore("almandine", "Almandine", "Fe3Al2Si3O12"),
            ore("alunite", "Alunite", "KAl3(SO4)2(OH)6"),
            ore("amethyst", "Amethyst", "SiO2"),
            ore("apatite", "Apatite", "Ca5(PO4)3F"),
            ore("asbestos", "Asbestos", "Mg3Si2H4O9"),
            ore("barite", "Barite", "BaSO4"),
            ore("bastnasite", "Bastnasite", "CeCO3F"),
            ore("bauxite", "Bauxite", "AlO(OH)"),
            ore("bentonite", "Bentonite", "NaMg3Si12H6(H2O)5O36"),
            ore("blue_topaz", "Blue topaz", "Al2SiO4(F,OH)2"),
            ore("bornite", "Bornite", "Cu5FeS4"),
            ore("calcite", "Calcite", "CaCO3"),
            ore("cassiterite", "Cassiterite", "SnO2"),
            ore("chalcocite", "Chalcocite", "Cu2S"),
            ore("chalcopyrite", "Chalcopyrite", "CuFeS2"),
            ore("chromite", "Chromite", "FeCr2O4"),
            ore("cinnabar", "Cinnabar", "HgS"),
            ore("coal", "Coal", "C"),
            ore("cobaltite", "Cobaltite", "CoAsS"),
            ore("cooperite", "Cooperite", "PtS"),
            ore("diamond", "Diamond", "C"),
            ore("diatomite", "Diatomite", "(SiO2)3(Fe2O3)(Al2O3)"),
            ore("electrotine", "Electrotine", "(SiFeS2)5(CrAl2O3)Hg3(MgAu)"),
            ore("emerald", "Emerald", "Be3Al2Si6O18"),
            ore("fullers_earth", "Fullers earth", "Mg2Si4O10H(H2O)"),
            ore("galena", "Galena", "PbS"),
            ore("garnierite", "Garnierite", "NiO"),
            ore("goethite", "Goethite", "FeO(OH)"),
            ore("graphite", "Graphite", "C"),
            ore("green_sapphire", "Green sapphire", "Al2O3"),
            ore("grossular", "Grossular", "Ca3Al2Si3O12"),
            ore("gypsum", "Gypsum", "CaSO4*2H2O"),
            ore("hematite", "Hematite", "Fe2O3"),
            ore("ilmenite", "Ilmenite", "FeTiO3"),
            ore("kyanite", "Kyanite", "Al2SiO5"),
            ore("lapis", "Lapis", "(Al6Si6Ca3Na8)2(Al6Si3Na3O12)(FeS2)(CaCO3)"),
            ore("lazurite", "Lazurite", "Na8Al6Si6O24S2"),
            ore("lepidolite", "Lepidolite", "KLi3Al4F2O10"),
            ore("limonite", "Limonite", "FeO(OH)*nH2O"),
            ore("magnetite", "Magnetite", "Fe3O4"),
            ore("magnesite", "Magnesite", "MgCO3"),
            ore("malachite", "Malachite", "Cu2CO3(OH)2"),
            ore("mica", "Mica", "KAl3Si3F2O10"),
            ore("molybdenite", "Molybdenite", "MoS2"),
            ore("monazite", "Monazite", "(Ce,La,Nd,Th)PO4"),
            ore("olivine", "Olivine", "(Mg,Fe)2SiO4"),
            ore("opal", "Opal", "SiO2*nH2O"),
            ore("pentlandite", "Pentlandite", "Ni9S8"),
            ore("pitchblende", "Pitchblende", "UO2"),
            ore("pollucite", "Pollucite", "CsAlSi2O6"),
            ore("powellite", "Powellite", "CaMoO4"),
            ore("pyrite", "Pyrite", "FeS2"),
            ore("pyrochlore", "Pyrochlore", "Ca2Nb2O6F"),
            ore("pyrolusite", "Pyrolusite", "MnO2"),
            ore("pyrope", "Pyrope", "Mg3Al2Si3O12"),
            ore("quartzite", "Quartzite", "SiO2"),
            ore("realgar", "Realgar", "As4S4"),
            ore("red_garnet", "Red garnet", "(Fe,Mg,Mn,Ca)3Al2Si3O12"),
            ore("redstone", "Redstone", "(SiFeS2)5(CrAl2O3)Hg3"),
            ore("sylvite", "Sylvite", "KCl"),
            ore("ruby", "Ruby", "Al2O3"),
            ore("halite", "Halite", "NaCl"),
            ore("saltpeter", "Saltpeter", "KNO3"),
            ore("sapphire", "Sapphire", "Al2O3"),
            ore("scheelite", "Scheelite", "CaWO4"),
            ore("soapstone", "Soapstone", "Mg3Si4O10(OH)2"),
            ore("sodalite", "Sodalite", "Na4Al3Si3ClO12"),
            ore("spessartine", "Spessartine", "Mn3Al2Si3O12"),
            ore("sphalerite", "Sphalerite", "ZnS"),
            ore("spodumene", "Spodumene", "LiAlSi2O6"),
            ore("stibnite", "Stibnite", "Sb2S3"),
            ore("sulfur", "Sulfur", "S"),
            ore("talc", "Talc", "Mg3Si4O10(OH)2"),
            ore("tantalite", "Tantalite", "(Fe,Mn)Ta2O6"),
            ore("tetrahedrite", "Tetrahedrite", "Cu2FeSbS3"),
            ore("topaz", "Topaz", "Al2SiO4(F,OH)2"),
            ore("tricalcium_phosphate", "Tricalcium phosphate", "Ca3(PO4)2"),
            ore("tungstate", "Tungstate", "WLi2O4"),
            ore("uraninite", "Uraninite", "UO2"),
            ore("vanadium_magnetite", "Vanadium magnetite", "(Fe3O4)V"),
            ore("wulfenite", "Wulfenite", "PbMoO4"),
            ore("yellow_garnet", "Yellow garnet", "(Fe,Mg,Mn,Ca)3Al2Si3O12"),
            ore("zeolite", "Zeolite", "Na2Al2Si3O10(H2O)2"),

            // Elemental ores: gameplay-friendly GT-style entries that represent
            // a target element directly rather than a specific natural mineral.
            ore("copper", "Copper", "Cu"),
            ore("gold", "Gold", "Au"),
            ore("iron", "Iron", "Fe"),
            ore("lead", "Lead", "Pb"),
            ore("nickel", "Nickel", "Ni"),
            ore("palladium", "Palladium", "Pd"),
            ore("platinum", "Platinum", "Pt"),
            ore("silver", "Silver", "Ag"),
            ore("tin", "Tin", "Sn")
    );

    public static final List<MineralResource> ALL = Stream.of(DEPOSITS, ORES)
            .flatMap(List::stream)
            .toList();

    public static final Map<String, MineralResource> BY_ID;
    public static final Map<ScannerList, List<MineralResource>> BY_CATEGORY;

    static {
        Map<String, MineralResource> byId = new LinkedHashMap<>();
        Map<ScannerList, List<MineralResource>> byCategory = new LinkedHashMap<>();

        for (MineralResource resource : ALL) {
            MineralResource previous = byId.putIfAbsent(resource.id(), resource);

            if (previous != null) {
                throw new IllegalStateException("Duplicate mineral resource id '" + resource.id() + "': "
                        + previous.englishName() + " and " + resource.englishName());
            }
        }

        byCategory.put(ScannerList.DEPOSIT, DEPOSITS);
        byCategory.put(ScannerList.ORE, ORES);

        BY_ID = Collections.unmodifiableMap(byId);
        BY_CATEGORY = Collections.unmodifiableMap(byCategory);
    }

    private NaturalResourceList() {
    }

    public static Optional<MineralResource> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    public static List<MineralResource> byCategory(ScannerList category) {
        return BY_CATEGORY.getOrDefault(category, List.of());
    }

    private static MineralResource resource(String id, String englishName, ScannerList category) {
        return new MineralResource(id, englishName, "???", category);
    }

    private static MineralResource ore(String id, String englishName, String formula) {
        return new MineralResource(id, englishName, formula, ScannerList.ORE);
    }
}
