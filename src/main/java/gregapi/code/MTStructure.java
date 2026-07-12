package gregapi.code;

import java.util.Locale;

/**
 * Immutable API-level description of a chemical element.
 *
 * <p>GT6 stores this data inside {@code OreDictMaterial}. For the port we keep
 * the element table separate first, so rocks, ores, fluids, gases, and later
 * generated material items can all reference the same facts.</p>
 *
 * @param atomicNumber amount of protons in the nucleus
 * @param id lowercase registry-safe id, for example {@code iron}
 * @param name English element name, for example {@code Iron}
 * @param symbol chemical symbol, for example {@code Fe}
 * @param protons proton count; normally equals {@code atomicNumber}
 * @param electrons neutral atom electron count; normally equals
 *                  {@code atomicNumber}
 * @param neutrons representative neutron count, usually rounded from the most
 *                 common isotope or atomic mass
 * @param massNumber representative mass number, normally protons + neutrons
 * @param meltingPointKelvin melting point in Kelvin, rounded for gameplay
 * @param boilingPointKelvin boiling point in Kelvin, rounded for gameplay
 * @param plasmaPointKelvin gameplay plasma threshold in Kelvin
 * @param densityGramsPerCubicCentimeter density near room temperature where
 *                                      meaningful; gases use approximate gas
 *                                      density converted to g/cm^3
 * @param category broad periodic-table category
 * @param tintColor RGB tint color without alpha
 */
public record MTStructure(
        int atomicNumber,
        String id,
        String name,
        String symbol,
        int protons,
        int electrons,
        int neutrons,
        int massNumber,
        long meltingPointKelvin,
        long boilingPointKelvin,
        long plasmaPointKelvin,
        double densityGramsPerCubicCentimeter,
        MTCategory category,
        int tintColor
) {
    public MTStructure {
        if (atomicNumber <= 0) {
            throw new IllegalArgumentException("Element atomic number must be positive: " + atomicNumber);
        }
        id = normalizeId(id);
        name = requireText(name, "name");
        symbol = requireSymbol(symbol);
        if (protons <= 0 || electrons < 0 || neutrons < 0 || massNumber <= 0) {
            throw new IllegalArgumentException("Element atomic stats must be non-negative and physically meaningful: " + name);
        }
        if (meltingPointKelvin < 0 || boilingPointKelvin < 0 || plasmaPointKelvin < 0) {
            throw new IllegalArgumentException("Element temperatures cannot be negative: " + name);
        }
        // Some elements are awkward in a simple gameplay phase model
        // (for example arsenic sublimes at normal pressure), so do not enforce
        // melting <= boiling here. Material systems can decide later how to
        // interpret those edge cases.
        if (plasmaPointKelvin > 0 && boilingPointKelvin > plasmaPointKelvin) {
            throw new IllegalArgumentException("Element boiling point cannot exceed plasma point: " + name);
        }
        if (densityGramsPerCubicCentimeter < 0) {
            throw new IllegalArgumentException("Element density cannot be negative: " + name);
        }
        if (category == null) {
            category = MTCategory.UNKNOWN;
        }
        if ((tintColor & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Element tint color must be RGB without alpha: 0x" + Integer.toHexString(tintColor));
        }
    }

    public int opaqueTintColor() {
        return 0xFF000000 | tintColor;
    }

    private static String normalizeId(String id) {
        String normalized = requireText(id, "id").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Element id must use only lowercase letters, numbers and underscores: " + id);
        }
        return normalized;
    }

    private static String requireSymbol(String symbol) {
        String value = requireText(symbol, "symbol");
        if (!value.matches("[A-Z][a-z]{0,2}")) {
            throw new IllegalArgumentException("Invalid chemical symbol: " + symbol);
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Element " + field + " cannot be blank");
        }
        return value;
    }
}
