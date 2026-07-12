package gregapi.code;

import java.util.List;

/**
 * GT6-style energy tags ported from original {@code gregapi.data.TD.Energy}.
 *
 * <p>This is only an API classification list. It mirrors GT6's default energy
 * kinds and useful groupings, but does not implement storage, packets, cables,
 * converters, explosions or capability logic yet.</p>
 */
public enum EnergyType {
    /**
     * Electricity. GT6 unit: EU.
     *
     * <p>GT6 meaning: size = voltage, amount = amperage.</p>
     */
    ELECTRICITY("ENERGY.ELECTRICITY", "EU", "Electric Energy"),

    /**
     * Rotational kinetic power. GT6 unit: RU.
     *
     * <p>GT6 meaning: size = speed/direction, amount = power.</p>
     */
    KINETIC_ROTATION("ENERGY.KINETIC_ROTATION", "RU", "Rotation Energy"),

    /**
     * Push/pull kinetic power. GT6 unit: KU.
     *
     * <p>GT6 meaning: size = push or pull direction, amount = power.</p>
     */
    KINETIC_PUSH("ENERGY.KINETIC_PUSH", "KU", "Kinetic Energy"),

    /**
     * Heat. GT6 unit: HU.
     *
     * <p>GT6 meaning: size is unused, amount = temperature.</p>
     */
    HEAT("ENERGY.HEAT", "HU", "Heat Energy"),

    /**
     * Cryo/cold energy. GT6 unit: CU.
     *
     * <p>GT6 meaning: size is unused, amount = temperature.</p>
     */
    CRYO("ENERGY.CRYO", "CU", "Cryo Energy"),

    /**
     * Laser/light energy. GT6 unit: LU.
     *
     * <p>GT6 meaning: size = beam strength/diameter/etc, amount = transmitted energy.</p>
     */
    LIGHT("ENERGY.LIGHT", "LU", "Light Energy"),

    /**
     * Magnetic field energy. GT6 unit: MU.
     *
     * <p>GT6 meaning: size = field strength/polarity, amount is unused.</p>
     */
    MAGNETIC("ENERGY.MAGNETIC", "MU", "Magnetic Energy"),

    /**
     * Neutron energy/rays. GT6 unit: NU.
     */
    NEUTRON("ENERGY.NEUTRON", "NU", "Neutron Energy"),

    /**
     * Quantum energy. GT6 unit: QU.
     */
    QUANTUM("ENERGY.QUANTUM", "QU", "Quantum Energy"),

    /**
     * Time/tick energy. GT6 unit: TU.
     *
     * <p>GT6 meaning: size is unused, amount = ticks.</p>
     */
    TIME("ENERGY.TIME", "TU", "Time"),

    /**
     * Steam power. GT6 unit label: Steam.
     */
    STEAM("ENERGY.STEAM", "Steam", "Steam"),

    /**
     * Air pressure power. GT6 unit: AU.
     *
     * <p>GT6 meaning: size = pressure, amount = amount of air.</p>
     */
    AIR("ENERGY.AIR", "AU", "Air Pressure"),

    /**
     * Thaumcraft Ordo Vis compatibility energy.
     */
    VIS_ORDO("ENERGY.VIS_ORDO", "Ordo", "Ordo Vis"),

    /**
     * Thaumcraft Aer Vis compatibility energy.
     */
    VIS_AER("ENERGY.VIS_AER", "Aer", "Aer Vis"),

    /**
     * Thaumcraft Aqua Vis compatibility energy.
     */
    VIS_AQUA("ENERGY.VIS_AQUA", "Aqua", "Aqua Vis"),

    /**
     * Thaumcraft Terra Vis compatibility energy.
     */
    VIS_TERRA("ENERGY.VIS_TERRA", "Terra", "Terra Vis"),

    /**
     * Thaumcraft Ignis Vis compatibility energy.
     */
    VIS_IGNIS("ENERGY.VIS_IGNIS", "Ignis", "Ignis Vis"),

    /**
     * Thaumcraft Perditio Vis compatibility energy.
     */
    VIS_PERDITIO("ENERGY.VIS_PERDITIO", "Perditio", "Perditio Vis");

    public static final EnergyType EU = ELECTRICITY;
    public static final EnergyType RU = KINETIC_ROTATION;
    public static final EnergyType KU = KINETIC_PUSH;
    public static final EnergyType HU = HEAT;
    public static final EnergyType CU = CRYO;
    public static final EnergyType LU = LIGHT;
    public static final EnergyType MU = MAGNETIC;
    public static final EnergyType NU = NEUTRON;
    public static final EnergyType QU = QUANTUM;
    public static final EnergyType TU = TIME;
    public static final EnergyType TICK = TIME;
    public static final EnergyType AU = AIR;

    public static final List<EnergyType> VIS = List.of(
            VIS_ORDO,
            VIS_AER,
            VIS_AQUA,
            VIS_TERRA,
            VIS_IGNIS,
            VIS_PERDITIO
    );

    /**
     * Contains all known GT6 default energy tags.
     */
    public static final List<EnergyType> ALL = List.of(
            VIS_ORDO,
            VIS_AER,
            VIS_AQUA,
            VIS_TERRA,
            VIS_IGNIS,
            VIS_PERDITIO,
            STEAM,
            AIR,
            QUANTUM,
            MAGNETIC,
            LIGHT,
            HEAT,
            CRYO,
            KINETIC_PUSH,
            KINETIC_ROTATION,
            ELECTRICITY,
            NEUTRON,
            TIME
    );

    /**
     * GT6 list of energy tags worth exactly 1 EU.
     */
    public static final List<EnergyType> ALL_EU = List.of(
            AIR,
            QUANTUM,
            MAGNETIC,
            LIGHT,
            KINETIC_PUSH,
            KINETIC_ROTATION,
            ELECTRICITY,
            HEAT,
            NEUTRON,
            CRYO
    );

    /**
     * GT6 list of energy tags actually used by GT6 itself.
     */
    public static final List<EnergyType> ALL_GT = List.of(
            AIR,
            QUANTUM,
            MAGNETIC,
            LIGHT,
            KINETIC_PUSH,
            KINETIC_ROTATION,
            ELECTRICITY,
            HEAT,
            NEUTRON,
            CRYO,
            TIME
    );

    public static final List<EnergyType> ALL_KINETIC = List.of(
            KINETIC_PUSH,
            KINETIC_ROTATION
    );

    public static final List<EnergyType> ALL_ELECTRIC = List.of(
            ELECTRICITY
    );

    public static final List<EnergyType> ALL_NEGATIVE_ALLOWED = List.of(
            AIR,
            QUANTUM,
            MAGNETIC,
            KINETIC_PUSH,
            KINETIC_ROTATION,
            ELECTRICITY
    );

    public static final List<EnergyType> ALL_WEAK_TO_WATER = List.of(
            ELECTRICITY,
            HEAT,
            CRYO
    );

    public static final List<EnergyType> ALL_WEAK_TO_THUNDER = List.of(
            ELECTRICITY
    );

    public static final List<EnergyType> ALL_WEAK_TO_FIRE = List.of(
            ELECTRICITY,
            CRYO
    );

    public static final List<EnergyType> ALL_HOT_COLD = List.of(
            HEAT,
            CRYO,
            VIS_IGNIS
    );

    public static final List<EnergyType> ALL_CONSUMPTION_LIMITED = List.of(
            ELECTRICITY,
            VIS_ORDO,
            VIS_AER,
            VIS_AQUA,
            VIS_TERRA,
            VIS_IGNIS,
            VIS_PERDITIO
    );

    public static final List<EnergyType> ALL_EXPLODING = List.of(
            AIR,
            QUANTUM,
            MAGNETIC,
            LIGHT,
            KINETIC_PUSH,
            KINETIC_ROTATION,
            ELECTRICITY,
            HEAT,
            NEUTRON,
            STEAM
    );

    public static final List<EnergyType> ALL_ALTERNATING = List.of(
            KINETIC_PUSH
    );

    public static final List<EnergyType> ALL_SIZE_IRRELEVANT = List.of(
            VIS_ORDO,
            VIS_AER,
            VIS_AQUA,
            VIS_TERRA,
            VIS_IGNIS,
            VIS_PERDITIO,
            STEAM,
            HEAT,
            CRYO,
            NEUTRON,
            QUANTUM,
            TIME
    );

    private final String tagId;
    private final String unit;
    private final String englishName;

    EnergyType(String tagId, String unit, String englishName) {
        this.tagId = tagId;
        this.unit = unit;
        this.englishName = englishName;
    }

    public String tagId() {
        return tagId;
    }

    public String unit() {
        return unit;
    }

    public String englishName() {
        return englishName;
    }
}
