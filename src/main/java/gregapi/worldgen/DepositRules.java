package gregapi.worldgen;

/**
 * API-level concepts for natural deposits that are not necessarily ore veins.
 *
 * <p>Concrete resource lists still live in {@code gregapi.data}. This file only
 * defines the stable vocabulary used to describe deposits such as clay pits,
 * black sand patches, turf and oilsands.</p>
 */
public final class DepositRules {
    private DepositRules() {
    }

    /**
     * Broad gameplay/geological family of a natural deposit.
     */
    public enum DepositKind {
        CLAY,
        SAND,
        BLACK_SAND,
        ORGANIC,
        OILSAND,
        EVAPORITE
    }

    /**
     * High-level placement model for a natural deposit.
     */
    public enum DepositPlacement {
        SURFACE_PATCH,
        SURFACE_PIT,
        UNDERGROUND_VEIN,
        LARGE_UNDERGROUND_LAYER
    }
}
