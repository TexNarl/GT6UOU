package gregapi.worldgen;

import java.util.List;
import java.util.Locale;

/**
 * API-level rules and value objects for GT-style ore veins.
 */
public final class OreVeinRules {
    private OreVeinRules() {
    }

    /**
     * API-level ore vein entry.
     *
     * <p>This describes which ore materials belong to one named vein. It does not
     * place blocks by itself; concrete worldgen decides how the definition is used.</p>
     */
    public record OreVeinDefinition(
            String id,
            String englishName,
            VeinDimensions dimension,
            String sourceLayerId,
            OreVeinShapeDefinition shape,
            OreVeinShapeSettings settings,
            List<String> hostRockIds,
            List<OreVeinOreEntry> entries
    ) {
        public OreVeinDefinition {
            id = normalizeId(id);
            sourceLayerId = normalizeId(sourceLayerId);
            if (dimension == null) {
                throw new IllegalArgumentException("Ore vein dimension cannot be null: " + id);
            }
            if (shape == null) {
                throw new IllegalArgumentException("Ore vein shape cannot be null: " + id);
            }
            if (settings == null) {
                throw new IllegalArgumentException("Ore vein settings cannot be null: " + id);
            }
            if (hostRockIds == null || hostRockIds.isEmpty()) {
                throw new IllegalArgumentException("Ore vein must contain at least one host rock: " + id);
            }
            if (entries == null || entries.isEmpty()) {
                throw new IllegalArgumentException("Ore vein must contain at least one ore entry: " + id);
            }
            hostRockIds = hostRockIds.stream()
                    .map(OreVeinDefinition::normalizeId)
                    .toList();
            hostRockIds = List.copyOf(hostRockIds);
            entries = List.copyOf(entries);
        }

        public List<String> oreIds() {
            return entries.stream()
                    .map(OreVeinOreEntry::oreId)
                    .distinct()
                    .toList();
        }

        private static String normalizeId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Ore vein id must not be blank");
            }
            return id.toLowerCase(Locale.ROOT).replace(' ', '_');
        }
    }

    /**
     * One material entry inside a vein generator.
     *
     * @param oreId ore/material id, for example {@code chalcopyrite}
     * @param role generator-specific role of this material
     * @param weight GTM material weight inside this generator role
     * @param minSize minimum GTM layer/role size, or {@code 0} when not applicable
     * @param maxSize maximum GTM layer/role size, or {@code 0} when not applicable
     * @param sourceMinY GTM per-entry min Y, used by dike generators; not used by
     *                   the rock-hosted placement model
     * @param sourceMaxY GTM per-entry max Y, used by dike generators; not used by
     *                   the rock-hosted placement model
     */
    public record OreVeinOreEntry(
            String oreId,
            OreVeinOreRole role,
            int weight,
            int minSize,
            int maxSize,
            Integer sourceMinY,
            Integer sourceMaxY
    ) {
        public OreVeinOreEntry {
            oreId = normalizeId(oreId);
            if (role == null) {
                throw new IllegalArgumentException("Ore vein entry role cannot be null: " + oreId);
            }
            if (weight < 0) {
                throw new IllegalArgumentException("Ore vein entry weight cannot be negative: " + oreId);
            }
            if (minSize < 0 || maxSize < 0 || minSize > maxSize) {
                throw new IllegalArgumentException("Invalid ore vein entry size range for " + oreId + ": " + minSize + ".." + maxSize);
            }
        }

        public static OreVeinOreEntry layer(String oreId, int weight, int minSize, int maxSize) {
            return new OreVeinOreEntry(oreId, OreVeinOreRole.LAYER, weight, minSize, maxSize, null, null);
        }

        public static OreVeinOreEntry ore(String oreId, int weight) {
            return new OreVeinOreEntry(oreId, OreVeinOreRole.ORE, weight, 0, 0, null, null);
        }

        public static OreVeinOreEntry rare(String oreId, int weight) {
            return new OreVeinOreEntry(oreId, OreVeinOreRole.RARE, weight, 0, 0, null, null);
        }

        public static OreVeinOreEntry dike(String oreId, int weight, int sourceMinY, int sourceMaxY) {
            return new OreVeinOreEntry(oreId, OreVeinOreRole.DIKE, weight, 0, 0, sourceMinY, sourceMaxY);
        }

        public static OreVeinOreEntry role(String oreId, OreVeinOreRole role, int size) {
            return new OreVeinOreEntry(oreId, role, 1, size, size, null, null);
        }

        private static String normalizeId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Ore vein ore id must not be blank");
            }
            return id.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        }
    }

    /**
     * Logical role of an ore material inside a GTM-style vein generator.
     */
    public enum OreVeinOreRole {
        LAYER,
        ORE,
        RARE,
        DIKE,
        PRIMARY,
        SECONDARY,
        BETWEEN,
        SPORADIC,
        TOP,
        MIDDLE,
        BOTTOM,
        SPREAD,
        HOST
    }

    /**
     * API-level description of how an ore vein is shaped.
     *
     * <p>The ids here are stable gameplay/data ids. They do not directly decide
     * which ore materials appear in the vein; material composition should be a
     * separate ore-vein definition layer.</p>
     *
     * @param id stable shape id, for example {@code mixed_cluster}
     * @param type broad geometry family
     * @param layered whether the shape is expected to preserve visible ore bands/layers
     * @param directional whether the shape has a meaningful axis, slope, or orientation
     * @param description short human/debug description
     */
    public record OreVeinShapeDefinition(
            String id,
            VeinShapeTypes type,
            boolean layered,
            boolean directional,
            String description
    ) {
        public OreVeinShapeDefinition {
            id = normalizeId(id);

            if (type == null) {
                throw new IllegalArgumentException("Ore vein shape type cannot be null: " + id);
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Ore vein shape description cannot be blank: " + id);
            }

            description = description.strip();
        }

        private static String normalizeId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Ore vein shape id cannot be blank");
            }

            String normalized = id.toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("Ore vein shape id must use only lowercase letters, numbers and underscores: " + id);
            }
            return normalized;
        }
    }

    /**
     * GTM vein generator parameters kept at API level.
     *
     * <p>{@code selectionWeight} is the weight used by this port when selecting
     * veins for a host rock. It is intentionally set independently from
     * {@code sourceGtmWeight}, because the current port design wants equal vein
     * chances inside each host rock.</p>
     */
    public record OreVeinShapeSettings(
            int clusterMin,
            int clusterMax,
            float density,
            int selectionWeight,
            int sourceGtmWeight,
            int sourceMinY,
            int sourceMaxY,
            Float rareBlockChance,
            Float veininessThreshold,
            Float maxRichnessThreshold,
            Float minRichness,
            Float maxRichness,
            Integer edgeRoundoffBegin,
            Float maxEdgeRoundoff
    ) {
        public OreVeinShapeSettings {
            if (clusterMin < 0 || clusterMax < clusterMin) {
                throw new IllegalArgumentException("Invalid vein cluster size: " + clusterMin + ".." + clusterMax);
            }
            if (selectionWeight <= 0) {
                throw new IllegalArgumentException("Vein selection weight must be positive");
            }
        }

        public static OreVeinShapeSettings basic(
                int clusterMin,
                int clusterMax,
                float density,
                int sourceGtmWeight,
                int sourceMinY,
                int sourceMaxY
        ) {
            return new OreVeinShapeSettings(clusterMin, clusterMax, density, 1, sourceGtmWeight, sourceMinY, sourceMaxY,
                    null, null, null, null, null, null, null);
        }

        public OreVeinShapeSettings withVeinedNoise(
                Float rareBlockChance,
                float veininessThreshold,
                float maxRichnessThreshold,
                float minRichness,
                float maxRichness,
                int edgeRoundoffBegin,
                float maxEdgeRoundoff
        ) {
            return new OreVeinShapeSettings(clusterMin, clusterMax, density, selectionWeight, sourceGtmWeight, sourceMinY, sourceMaxY,
                    rareBlockChance, veininessThreshold, maxRichnessThreshold, minRichness, maxRichness, edgeRoundoffBegin, maxEdgeRoundoff);
        }
    }

    /**
     * Vanilla dimensions used by GTM ore vein lists.
     */
    public enum VeinDimensions {
        OVERWORLD,
        NETHER,
        END
    }

    /**
     * Runtime size tier for a generated instance of an ore vein.
     *
     * <p>The raw {@link OreVeinShapeSettings#clusterMin()} and
     * {@link OreVeinShapeSettings#clusterMax()} values describe the small/base
     * size. Worldgen can choose a larger tier per generated vein instance
     * without duplicating vein definitions in data lists.</p>
     */
    public enum OreVeinSize {
        SMALL(1.0F),
        MEDIUM(1.35F),
        LARGE(1.65F);

        private final float scale;

        OreVeinSize(float scale) {
            this.scale = scale;
        }

        public int scaleSize(int baseSize) {
            return Math.max(1, Math.round(baseSize * scale));
        }
    }

    /**
     * Broad geometry family for an ore vein.
     *
     * <p>This is intentionally an API concept, not a generator implementation.
     * Concrete worldgen code can interpret these families with NeoForge features,
     * chunk hooks, or a later data-driven generator.</p>
     */
    public enum VeinShapeTypes {
        /**
         * Ores are distributed inside one noisy blob and selected by weight.
         *
         * <p>Closest references:
         * GTM {@code veinedVeinGenerator}; TFG {@code tfc:cluster_vein}.</p>
         */
        MIXED_CLUSTER,

        /**
         * Multiple ore materials form broad layers or bands inside one deposit.
         *
         * <p>Closest references:
         * GTM {@code layeredVeinGenerator}; TFG large disc/layer style veins.</p>
         */
        LAYERED,

        /**
         * GT-style classic vein with top, middle, bottom, between and sporadic ore roles.
         *
         * <p>Closest reference: GTM {@code classicVeinGenerator}, for example diamond veins.</p>
         */
        CLASSIC_BANDED,

        /**
         * Long narrow intrusive sheet/cut, usually steep or slanted.
         *
         * <p>Closest reference: GTM {@code dikeVeinGenerator}.</p>
         */
        DIKE,

        /**
         * Rough cuboid volume with top/middle/bottom/spread roles.
         *
         * <p>Closest reference: GTM {@code cuboidVeinGenerator}.</p>
         */
        CUBOID,

        /**
         * Thin flattened lens or disc.
         *
         * <p>Closest reference: TFG {@code tfc:disc_vein}.</p>
         */
        DISC,

        /**
         * Vertical or steeply tilted pipe-like body.
         *
         * <p>Closest reference: TFG {@code tfc:pipe_vein}.</p>
         */
        PIPE,

        /**
         * Shell/core cavity-style body.
         *
         * <p>Closest reference: GTM {@code GeodeVeinGenerator}. This is not used by
         * the current GTM ore list, but the generator exists and is useful to keep
         * as an API shape for later crystals/geodes.</p>
         */
        GEODE
    }
}
