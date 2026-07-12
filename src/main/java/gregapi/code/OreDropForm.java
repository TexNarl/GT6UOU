package gregapi.code;

/**
 * Drop form produced by an ore block when mined.
 *
 * <p>Most ores drop raw material, like modern vanilla iron/copper/gold ores,
 * but raw material can have different quality/yield. A small amount of minerals
 * can later be switched to crystals without changing the ore block registry
 * itself.</p>
 */
public enum OreDropForm {
    POOR_RAW_MATERIAL("poor_raw_", "", "Poor raw ", "", 7),
    RAW_MATERIAL("raw_", "", "Raw ", "", 9),
    RICH_RAW_MATERIAL("rich_raw_", "", "Rich raw ", "", 18),
    CRYSTAL("", "_crystal", "", " crystal");

    private final String idPrefix;
    private final String idSuffix;
    private final String namePrefix;
    private final String nameSuffix;
    private final int nuggetEquivalent;

    OreDropForm(String idPrefix, String idSuffix, String namePrefix, String nameSuffix) {
        this(idPrefix, idSuffix, namePrefix, nameSuffix, 0);
    }

    OreDropForm(String idPrefix, String idSuffix, String namePrefix, String nameSuffix, int nuggetEquivalent) {
        this.idPrefix = idPrefix;
        this.idSuffix = idSuffix;
        this.namePrefix = namePrefix;
        this.nameSuffix = nameSuffix;
        this.nuggetEquivalent = nuggetEquivalent;
    }

    public String itemId(MineralResource ore) {
        return idPrefix + ore.id() + idSuffix;
    }

    public String englishName(MineralResource ore) {
        return namePrefix + ore.englishName() + nameSuffix;
    }

    public int nuggetEquivalent() {
        return nuggetEquivalent;
    }

    public boolean isRawMaterial() {
        return this == POOR_RAW_MATERIAL || this == RAW_MATERIAL || this == RICH_RAW_MATERIAL;
    }
}
