package gregapi.code;

import gregapi.data.RocksList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Locale;

/**
 * Shared API definitions for generated natural block type lists.
 *
 * <p>This file contains the mechanics of what each listed type means: record
 * shapes, validation, tint handling and vanilla-like block properties. The
 * actual lists live in {@link RocksList}.</p>
 */
public final class BlockTypeDefinitions {
    private BlockTypeDefinitions() {
    }

    public static RockType rock(String id, String englishName) {
        return rock(id, englishName, 0xFFFFFF);
    }

    public static RockType rock(String id, String englishName, int tintColor) {
        return new RockType(normalizeId(id, "Rock"), requireText(englishName, "Rock", "englishName"), normalizeRgb(tintColor, "Rock"));
    }

    public static ClayType clay(String id, String englishName, int tintColor) {
        return new ClayType(normalizeId(id, "Clay"), requireText(englishName, "Clay", "englishName"), normalizeRgb(tintColor, "Clay"));
    }

    public static SandType sand(String id, String englishName, int tintColor) {
        return new SandType(normalizeId(id, "Sand"), requireText(englishName, "Sand", "englishName"), normalizeRgb(tintColor, "Sand"));
    }

    public static OtherType other(String id, String englishName, int tintColor) {
        return new OtherType(normalizeId(id, "Other type"), requireText(englishName, "Other type", "englishName"), normalizeRgb(tintColor, "Other type"));
    }

    private static int normalizeRgb(int tintColor, String typeName) {
        if ((tintColor & 0xFF000000) != 0) {
            throw new IllegalArgumentException(typeName + " tint color must be RGB without alpha: 0x" + Integer.toHexString(tintColor));
        }
        return tintColor;
    }

    private static String normalizeId(String id, String typeName) {
        String normalized = requireText(id, typeName, "id").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(typeName + " id must use only lowercase letters, numbers and underscores: " + id);
        }
        return normalized;
    }

    private static String requireText(String value, String typeName, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(typeName + " " + field + " cannot be blank");
        }
        return value;
    }

    /**
     * A stone rock type definition.
     *
     * @param id lowercase registry-safe id, for example {@code black_granite}
     * @param englishName human-readable English name, for example {@code Black granite}
     * @param tintColor RGB tint color for block and item color handlers
     */
    public record RockType(String id, String englishName, int tintColor) {
        /**
         * Creates block properties matching vanilla Stone.
         */
        public BlockBehaviour.Properties stoneBlockProperties() {
            return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE);
        }

        /**
         * Returns the tint color with a fully opaque alpha channel.
         */
        public int opaqueTintColor() {
            return 0xFF000000 | tintColor;
        }
    }

    /**
     * A clay type definition.
     *
     * @param id lowercase registry-safe id, for example {@code brown_clay}
     * @param englishName human-readable English name, for example {@code Brown clay}
     * @param tintColor RGB tint color for block and item color handlers
     */
    public record ClayType(String id, String englishName, int tintColor) {
        /**
         * Creates block properties matching vanilla Clay.
         */
        public BlockBehaviour.Properties clayBlockProperties() {
            return BlockBehaviour.Properties.ofFullCopy(Blocks.CLAY);
        }

        /**
         * Returns the tint color with a fully opaque alpha channel.
         */
        public int opaqueTintColor() {
            return 0xFF000000 | tintColor;
        }
    }

    /**
     * A sand type definition.
     *
     * @param id lowercase registry-safe id, for example {@code black_sand}
     * @param englishName human-readable English name, for example {@code Black sand}
     * @param tintColor RGB tint color for block and item color handlers
     */
    public record SandType(String id, String englishName, int tintColor) {
        /**
         * Creates block properties matching vanilla Sand.
         */
        public BlockBehaviour.Properties sandBlockProperties() {
            return BlockBehaviour.Properties.ofFullCopy(Blocks.SAND);
        }

        /**
         * Returns the tint color with a fully opaque alpha channel.
         */
        public int opaqueTintColor() {
            return 0xFF000000 | tintColor;
        }
    }

    /**
     * A miscellaneous terrain-like type definition.
     *
     * @param id lowercase registry-safe id, for example {@code turf}
     * @param englishName human-readable English name, for example {@code Turf}
     * @param tintColor RGB tint color for block and item color handlers
     */
    public record OtherType(String id, String englishName, int tintColor) {
        /**
         * Creates block properties matching vanilla Dirt.
         */
        public BlockBehaviour.Properties dirtBlockProperties() {
            return BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT);
        }

        /**
         * Returns the tint color with a fully opaque alpha channel.
         */
        public int opaqueTintColor() {
            return 0xFF000000 | tintColor;
        }
    }
}
