package gregapi.worldgen;

import java.util.List;
import java.util.Locale;

/**
 * API-level rules for future block-like gases.
 *
 * <p>Gases are intended to be world blocks/mediums rather than ordinary fluid
 * stacks. A gas can rise or sink through air spaces, collect under ceilings or
 * in pits, diffuse, poison entities, burn or explode. This file only describes
 * the concept; ticking behavior and block registration belong to the mod
 * implementation layer.</p>
 */
public final class GasRules {
    private GasRules() {
    }

    public record GasDefinition(
            String id,
            String englishName,
            String formula,
            GasBuoyancy buoyancy,
            int riseOrSinkRate,
            int diffusionRate,
            int dissipationRate,
            int toxicity,
            int flammability,
            int explosiveness,
            boolean displacesAir,
            boolean extinguishesFlames,
            List<GasReaction> reactions
    ) {
        public GasDefinition {
            id = normalizeId(id);
            englishName = requireText(englishName, "englishName");
            formula = requireText(formula, "formula");
            if (buoyancy == null) {
                throw new IllegalArgumentException("Gas buoyancy cannot be null: " + id);
            }
            riseOrSinkRate = requireRange(riseOrSinkRate, 0, 100, "riseOrSinkRate", id);
            diffusionRate = requireRange(diffusionRate, 0, 100, "diffusionRate", id);
            dissipationRate = requireRange(dissipationRate, 0, 100, "dissipationRate", id);
            toxicity = requireRange(toxicity, 0, 100, "toxicity", id);
            flammability = requireRange(flammability, 0, 100, "flammability", id);
            explosiveness = requireRange(explosiveness, 0, 100, "explosiveness", id);
            reactions = List.copyOf(reactions);
        }

        public boolean rises() {
            return buoyancy == GasBuoyancy.RISES;
        }

        public boolean sinks() {
            return buoyancy == GasBuoyancy.SINKS;
        }

        public boolean isDangerous() {
            return toxicity > 0 || flammability > 0 || explosiveness > 0;
        }
    }

    public enum GasBuoyancy {
        /**
         * Lighter than air: tends to climb and collect under ceilings.
         */
        RISES,

        /**
         * Close to air density: tends to spread sideways and linger.
         */
        NEUTRAL,

        /**
         * Heavier than air: tends to sink and collect in pits/cave floors.
         */
        SINKS
    }

    public record GasReaction(
            GasReactionTrigger trigger,
            String resultGasId,
            int chance,
            int minimumConcentration
    ) {
        public GasReaction {
            if (trigger == null) {
                throw new IllegalArgumentException("Gas reaction trigger cannot be null");
            }
            resultGasId = resultGasId == null || resultGasId.isBlank() ? "" : normalizeId(resultGasId);
            chance = requireRange(chance, 0, 100, "chance", trigger.name());
            minimumConcentration = requireRange(minimumConcentration, 0, 100, "minimumConcentration", trigger.name());
        }
    }

    public enum GasReactionTrigger {
        FIRE,
        SPARK,
        EXPLOSION,
        WATER,
        LIVING_ENTITY,
        OPEN_SKY
    }

    private static String normalizeId(String id) {
        String normalized = requireText(id, "id").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Gas id must use only lowercase letters, numbers and underscores: " + id);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Gas " + field + " cannot be blank");
        }
        return value;
    }

    private static int requireRange(int value, int min, int max, String field, String id) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Gas " + field + " must be " + min + ".." + max + ": " + id);
        }

        return value;
    }
}
