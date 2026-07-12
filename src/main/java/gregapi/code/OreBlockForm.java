package gregapi.code;

/**
 * Natural block forms that can represent the same ore/mineral resource in the world.
 *
 * <p>Example for copper:
 * {@code poor_copper_ore}, {@code copper_ore}, {@code rich_copper_ore},
 * {@code raw_copper_block}. The mineral id is still {@code copper}; this enum
 * only describes the world block form.</p>
 */
public enum OreBlockForm {
    POOR("poor_", "_ore", "Poor ", " ore", false),
    NORMAL("", "_ore", "", " ore", false),
    RICH("rich_", "_ore", "Rich ", " ore", false),
    RAW_BLOCK("raw_", "_block", "Raw ", " block", true);

    private final String idPrefix;
    private final String idSuffix;
    private final String namePrefix;
    private final String nameSuffix;
    private final boolean compactRawBlock;

    OreBlockForm(String idPrefix, String idSuffix, String namePrefix, String nameSuffix, boolean compactRawBlock) {
        this.idPrefix = idPrefix;
        this.idSuffix = idSuffix;
        this.namePrefix = namePrefix;
        this.nameSuffix = nameSuffix;
        this.compactRawBlock = compactRawBlock;
    }

    public String blockId(MineralResource ore) {
        return idPrefix + ore.id() + idSuffix;
    }

    public String englishName(MineralResource ore) {
        return namePrefix + ore.englishName() + nameSuffix;
    }

    public boolean isCompactRawBlock() {
        return compactRawBlock;
    }

    public static String mineralIdFromBlockId(String blockId) {
        if (blockId.startsWith(POOR.idPrefix) && blockId.endsWith(POOR.idSuffix)) {
            return strip(blockId, POOR.idPrefix, POOR.idSuffix);
        }
        if (blockId.startsWith(RICH.idPrefix) && blockId.endsWith(RICH.idSuffix)) {
            return strip(blockId, RICH.idPrefix, RICH.idSuffix);
        }
        if (blockId.startsWith(RAW_BLOCK.idPrefix) && blockId.endsWith(RAW_BLOCK.idSuffix)) {
            return strip(blockId, RAW_BLOCK.idPrefix, RAW_BLOCK.idSuffix);
        }
        if (blockId.endsWith(NORMAL.idSuffix)) {
            return strip(blockId, NORMAL.idPrefix, NORMAL.idSuffix);
        }

        return blockId;
    }

    public static OreBlockForm fromBlockId(String blockId) {
        if (blockId.startsWith(POOR.idPrefix) && blockId.endsWith(POOR.idSuffix)) {
            return POOR;
        }
        if (blockId.startsWith(RICH.idPrefix) && blockId.endsWith(RICH.idSuffix)) {
            return RICH;
        }
        if (blockId.startsWith(RAW_BLOCK.idPrefix) && blockId.endsWith(RAW_BLOCK.idSuffix)) {
            return RAW_BLOCK;
        }
        if (blockId.endsWith(NORMAL.idSuffix)) {
            return NORMAL;
        }

        return NORMAL;
    }

    public static boolean isOreLikeBlockId(String blockId) {
        return blockId.endsWith(NORMAL.idSuffix)
                || blockId.startsWith(RAW_BLOCK.idPrefix) && blockId.endsWith(RAW_BLOCK.idSuffix);
    }

    private static String strip(String blockId, String prefix, String suffix) {
        return blockId.substring(prefix.length(), blockId.length() - suffix.length());
    }
}
