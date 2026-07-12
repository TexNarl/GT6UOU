package gregapi.code;

import gregapi.tools.ScannerList;

import java.util.Locale;

/**
 * API-level description of a useful natural resource.
 *
 * <p>This intentionally stores stable resource ids instead of direct block
 * instances. Registries, scanners, and worldgen can resolve those ids into
 * blocks later without making this API depend on a specific registration
 * phase.</p>
 *
 * @param id lowercase registry-safe id, usually matching the block id
 * @param englishName human-readable English name
 * @param formula chemical formula, or {@code ???} if the source material definition does not expose one yet
 * @param category resource category used by scanners and filters
 */
public record MineralResource(String id, String englishName, String formula, ScannerList category) {
    public MineralResource {
        id = normalizeId(id);
        englishName = requireText(englishName, "englishName");
        formula = requireText(formula, "formula");
        if (category == null) {
            throw new IllegalArgumentException("Mineral resource category cannot be null: " + id);
        }
    }

    private static String normalizeId(String id) {
        String normalized = requireText(id, "id").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Mineral resource id must use only lowercase letters, numbers and underscores: " + id);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mineral resource " + field + " cannot be blank");
        }
        return value;
    }
}
