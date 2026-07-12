package gregapi.worldgen;

/**
 * Shared geological classifications used by stone-layer world generation.
 *
 * <p>This file intentionally keeps broad geology concepts together: one enum
 * classifies individual rock types, the other classifies large surface
 * geological regions used to pick layer stacks.</p>
 */
public final class GeologicalRules {
    private GeologicalRules() {
    }

    /**
     * Geological group for a rock type used by stone-layer world generation.
     *
     * <p>The group is not just cosmetic: ore rules can use it to say things
     * like "cassiterite prefers felsic intrusive rocks" or "limonite prefers
     * sedimentary rocks".</p>
     */
    public enum RockGroup {
        /**
         * Deep-cooled igneous rocks: granite, diorite, gabbro.
         */
        IGNEOUS_INTRUSIVE,

        /**
         * Surface/near-surface volcanic rocks: basalt, andesite, rhyolite, tuff.
         */
        IGNEOUS_EXTRUSIVE,

        /**
         * Deposited rocks: shale, limestone, chalk, chert, claystone.
         */
        SEDIMENTARY,

        /**
         * Altered rocks produced by heat/pressure: slate, schist, gneiss, marble.
         */
        METAMORPHIC,

        /**
         * Rare rocks with special generation meaning rather than a normal broad
         * host-rock role.
         */
        SPECIAL
    }

    /**
     * Broad surface geological setting used to pick the first rock layer in an
     * Overworld rock stack.
     *
     * <p>This is intentionally not a Minecraft biome. One biome can contain more
     * than one geological region, and one geological region can pass through many
     * biomes.</p>
     */
    public enum RegionType {
        /**
         * Normal continental crust with sedimentary rocks near the surface.
         */
        CONTINENTAL_SEDIMENTARY,

        /**
         * Volcanic or oceanic-style crust with extrusive igneous rocks near the surface.
         */
        VOLCANIC,

        /**
         * Mountain/uplift regions where deeper metamorphic or intrusive rocks can
         * appear at or near the surface.
         */
        UPLIFT,

        /**
         * Rare mafic/ultramafic regions for basalt-gabbro-komatiite-kimberlite style stacks.
         */
        MAFIC_DEEP
    }
}
