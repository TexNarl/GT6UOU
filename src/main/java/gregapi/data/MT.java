package gregapi.data;

import gregapi.code.MTCategory;
import gregapi.code.MTStructure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * API-level chemical element table.
 *
 * <p>This is the beginning of the GT6UOU equivalent of the elemental part of
 * {@code gregapi.data.MT}. Values are rounded enough for gameplay and material
 * balancing. The table deliberately starts with elements that are immediately
 * useful for rocks, clays, sands, common ores, fuels, and early metallurgy.</p>
 */
public final class MT {
    public static final MTStructure H = element(1, "hydrogen", "Hydrogen", "H", 0, 14, 20, 0.00008988, MTCategory.REACTIVE_NONMETAL, 0xFFFFFF);
    public static final MTStructure He = element(2, "helium", "Helium", "He", 2, 1, 4, 0.0001785, MTCategory.NOBLE_GAS, 0xD9FFFF);
    public static final MTStructure Li = element(3, "lithium", "Lithium", "Li", 4, 454, 1615, 0.534, MTCategory.ALKALI_METAL, 0xCC80FF);
    public static final MTStructure Be = element(4, "beryllium", "Beryllium", "Be", 5, 1560, 2742, 1.85, MTCategory.ALKALINE_EARTH_METAL, 0xC2FF00);
    public static final MTStructure B = element(5, "boron", "Boron", "B", 6, 2349, 4200, 2.34, MTCategory.METALLOID, 0xFFB5B5);
    public static final MTStructure C = element(6, "carbon", "Carbon", "C", 6, 3915, 4300, 2.267, MTCategory.REACTIVE_NONMETAL, 0x303030);
    public static final MTStructure N = element(7, "nitrogen", "Nitrogen", "N", 7, 63, 77, 0.0012506, MTCategory.REACTIVE_NONMETAL, 0x3050F8);
    public static final MTStructure O = element(8, "oxygen", "Oxygen", "O", 8, 54, 90, 0.001429, MTCategory.REACTIVE_NONMETAL, 0xFF0D0D);
    public static final MTStructure F = element(9, "fluorine", "Fluorine", "F", 10, 53, 85, 0.001696, MTCategory.REACTIVE_NONMETAL, 0x90E050);
    public static final MTStructure Ne = element(10, "neon", "Neon", "Ne", 10, 25, 27, 0.0008999, MTCategory.NOBLE_GAS, 0xB3E3F5);

    public static final MTStructure Na = element(11, "sodium", "Sodium", "Na", 12, 371, 1156, 0.968, MTCategory.ALKALI_METAL, 0xAB5CF2);
    public static final MTStructure Mg = element(12, "magnesium", "Magnesium", "Mg", 12, 923, 1363, 1.738, MTCategory.ALKALINE_EARTH_METAL, 0x8AFF00);
    public static final MTStructure Al = element(13, "aluminium", "Aluminium", "Al", 14, 933, 2792, 2.70, MTCategory.POST_TRANSITION_METAL, 0xBFA6A6);
    public static final MTStructure Si = element(14, "silicon", "Silicon", "Si", 14, 1687, 3538, 2.3296, MTCategory.METALLOID, 0xF0C8A0);
    public static final MTStructure P = element(15, "phosphorus", "Phosphorus", "P", 16, 317, 554, 1.823, MTCategory.REACTIVE_NONMETAL, 0xFF8000);
    public static final MTStructure S = element(16, "sulfur", "Sulfur", "S", 16, 388, 718, 2.07, MTCategory.REACTIVE_NONMETAL, 0xFFFF30);
    public static final MTStructure Cl = element(17, "chlorine", "Chlorine", "Cl", 18, 172, 239, 0.0032, MTCategory.REACTIVE_NONMETAL, 0x1FF01F);
    public static final MTStructure Ar = element(18, "argon", "Argon", "Ar", 22, 84, 87, 0.001784, MTCategory.NOBLE_GAS, 0x80D1E3);

    public static final MTStructure K = element(19, "potassium", "Potassium", "K", 20, 337, 1032, 0.862, MTCategory.ALKALI_METAL, 0x8F40D4);
    public static final MTStructure Ca = element(20, "calcium", "Calcium", "Ca", 20, 1115, 1757, 1.54, MTCategory.ALKALINE_EARTH_METAL, 0x3DFF00);
    public static final MTStructure Ti = element(22, "titanium", "Titanium", "Ti", 26, 1941, 3560, 4.506, MTCategory.TRANSITION_METAL, 0xBFC2C7);
    public static final MTStructure Cr = element(24, "chromium", "Chromium", "Cr", 28, 2180, 2944, 7.19, MTCategory.TRANSITION_METAL, 0x8A99C7);
    public static final MTStructure Mn = element(25, "manganese", "Manganese", "Mn", 30, 1519, 2334, 7.21, MTCategory.TRANSITION_METAL, 0x9C7AC7);
    public static final MTStructure Fe = element(26, "iron", "Iron", "Fe", 30, 1811, 3134, 7.874, MTCategory.TRANSITION_METAL, 0xD8AF93);
    public static final MTStructure Co = element(27, "cobalt", "Cobalt", "Co", 32, 1768, 3200, 8.90, MTCategory.TRANSITION_METAL, 0xF090A0);
    public static final MTStructure Ni = element(28, "nickel", "Nickel", "Ni", 31, 1728, 3186, 8.908, MTCategory.TRANSITION_METAL, 0x50D050);
    public static final MTStructure Cu = element(29, "copper", "Copper", "Cu", 35, 1358, 2835, 8.96, MTCategory.TRANSITION_METAL, 0xC88033);
    public static final MTStructure Zn = element(30, "zinc", "Zinc", "Zn", 35, 693, 1180, 7.14, MTCategory.TRANSITION_METAL, 0x7D80B0);
    public static final MTStructure As = element(33, "arsenic", "Arsenic", "As", 42, 1090, 887, 5.727, MTCategory.METALLOID, 0xBD80E3);

    public static final MTStructure Sr = element(38, "strontium", "Strontium", "Sr", 50, 1050, 1655, 2.64, MTCategory.ALKALINE_EARTH_METAL, 0x00FF00);
    public static final MTStructure Zr = element(40, "zirconium", "Zirconium", "Zr", 51, 2128, 4682, 6.52, MTCategory.TRANSITION_METAL, 0x94E0E0);
    public static final MTStructure Mo = element(42, "molybdenum", "Molybdenum", "Mo", 54, 2896, 4912, 10.28, MTCategory.TRANSITION_METAL, 0x54B5B5);
    public static final MTStructure Ag = element(47, "silver", "Silver", "Ag", 61, 1235, 2435, 10.49, MTCategory.TRANSITION_METAL, 0xC0C0C0);
    public static final MTStructure Sn = element(50, "tin", "Tin", "Sn", 69, 505, 2875, 7.287, MTCategory.POST_TRANSITION_METAL, 0x668080);
    public static final MTStructure Ba = element(56, "barium", "Barium", "Ba", 81, 1000, 2170, 3.51, MTCategory.ALKALINE_EARTH_METAL, 0x00C900);
    public static final MTStructure W = element(74, "tungsten", "Tungsten", "W", 110, 3695, 5828, 19.25, MTCategory.TRANSITION_METAL, 0x2194D6);
    public static final MTStructure Au = element(79, "gold", "Gold", "Au", 118, 1337, 3129, 19.30, MTCategory.TRANSITION_METAL, 0xFFD123);
    public static final MTStructure Hg = element(80, "mercury", "Mercury", "Hg", 121, 234, 630, 13.534, MTCategory.TRANSITION_METAL, 0xB8B8D0);
    public static final MTStructure Pb = element(82, "lead", "Lead", "Pb", 125, 601, 2022, 11.34, MTCategory.POST_TRANSITION_METAL, 0x575961);
    public static final MTStructure U = element(92, "uranium", "Uranium", "U", 146, 1405, 4404, 19.1, MTCategory.ACTINIDE, 0x008F00);

    public static final List<MTStructure> ELEMENTS = List.of(
            H, He, Li, Be, B, C, N, O, F, Ne,
            Na, Mg, Al, Si, P, S, Cl, Ar,
            K, Ca, Ti, Cr, Mn, Fe, Co, Ni, Cu, Zn, As,
            Sr, Zr, Mo, Ag, Sn, Ba, W, Au, Hg, Pb, U
    );

    public static final Map<String, MTStructure> BY_ID;
    public static final Map<String, MTStructure> BY_SYMBOL;
    public static final Map<Integer, MTStructure> BY_ATOMIC_NUMBER;

    static {
        Map<String, MTStructure> byId = new LinkedHashMap<>();
        Map<String, MTStructure> bySymbol = new LinkedHashMap<>();
        Map<Integer, MTStructure> byAtomicNumber = new LinkedHashMap<>();

        for (MTStructure element : ELEMENTS) {
            putUnique(byId, element.id(), element, "id");
            putUnique(bySymbol, element.symbol(), element, "symbol");
            putUnique(byAtomicNumber, element.atomicNumber(), element, "atomic number");
        }

        BY_ID = Collections.unmodifiableMap(byId);
        BY_SYMBOL = Collections.unmodifiableMap(bySymbol);
        BY_ATOMIC_NUMBER = Collections.unmodifiableMap(byAtomicNumber);
    }

    private MT() {
    }

    public static Optional<MTStructure> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    public static Optional<MTStructure> bySymbol(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_SYMBOL.get(symbol));
    }

    public static Optional<MTStructure> byAtomicNumber(int atomicNumber) {
        return Optional.ofNullable(BY_ATOMIC_NUMBER.get(atomicNumber));
    }

    private static MTStructure element(
            int atomicNumber,
            String id,
            String name,
            String symbol,
            int neutrons,
            long meltingPointKelvin,
            long boilingPointKelvin,
            double densityGramsPerCubicCentimeter,
            MTCategory category,
            int tintColor
    ) {
        int plasmaPointKelvin = boilingPointKelvin > 0 ? Math.toIntExact(Math.min(Integer.MAX_VALUE, boilingPointKelvin * 100)) : 0;

        return new MTStructure(
                atomicNumber,
                id,
                name,
                symbol,
                atomicNumber,
                atomicNumber,
                neutrons,
                atomicNumber + neutrons,
                meltingPointKelvin,
                boilingPointKelvin,
                plasmaPointKelvin,
                densityGramsPerCubicCentimeter,
                category,
                tintColor
        );
    }

    private static <K> void putUnique(Map<K, MTStructure> map, K key, MTStructure element, String keyName) {
        MTStructure previous = map.putIfAbsent(key, element);

        if (previous != null) {
            throw new IllegalStateException("Duplicate element " + keyName + " '" + key + "': " + previous.name() + " and " + element.name());
        }
    }
}
