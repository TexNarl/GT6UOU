package gregapi.data;

import gregapi.worldgen.GasRules.GasBuoyancy;
import gregapi.worldgen.GasRules.GasDefinition;
import gregapi.worldgen.GasRules.GasReaction;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API-level list of block-like world gases.
 *
 * <p>This list is intentionally empty for now. Future entries can describe
 * gases such as carbon monoxide, methane or hydrogen sulfide without implying
 * that their blocks/ticking behavior are registered yet.</p>
 */
public final class GasList {
    public static final List<GasDefinition> GASES = List.of(
    );

    public static final Map<String, GasDefinition> BY_ID = GASES.stream()
            .collect(Collectors.toUnmodifiableMap(GasDefinition::id, Function.identity()));

    private GasList() {
    }

    public static GasDefinition byId(String id) {
        return BY_ID.get(id);
    }

    @SuppressWarnings("unused")
    private static GasDefinition gas(
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
        return new GasDefinition(id, englishName, formula, buoyancy, riseOrSinkRate, diffusionRate, dissipationRate,
                toxicity, flammability, explosiveness, displacesAir, extinguishesFlames, reactions);
    }
}
