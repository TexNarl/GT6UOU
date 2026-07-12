package gregapi.data;

import gregapi.worldgen.BedrockOreRules.BedrockOreDefinition;
import gregapi.worldgen.BedrockOreRules.BiomeWeightModifier;
import gregapi.worldgen.BedrockOreRules.WeightedOreResource;
import gregtech.worldgen.GTDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Data list for abstract bedrock ore veins.
 *
 * <p>The list is intentionally empty for now: the API and saved-data mechanics
 * exist, but no bedrock ore vein is enabled until we explicitly add entries.</p>
 */
public final class BedrockOreList {
    public static final List<BedrockOreDefinition> OVERWORLD = List.of(
    );

    public static final Map<String, BedrockOreDefinition> BY_ID = OVERWORLD.stream()
            .collect(Collectors.toUnmodifiableMap(BedrockOreDefinition::id, Function.identity()));

    static {
        validateOreResources();
    }

    private BedrockOreList() {
    }

    public static BedrockOreDefinition byId(String id) {
        return BY_ID.get(id);
    }

    @SuppressWarnings("unused")
    private static BedrockOreDefinition bedrockOre(
            String id,
            String englishName,
            int weight,
            int size,
            int minimumYield,
            int maximumYield,
            int depletionAmount,
            int depletionChance,
            int depletedYield,
            List<WeightedOreResource> ores,
            Set<ResourceKey<Level>> dimensions,
            List<BiomeWeightModifier> biomeWeightModifiers
    ) {
        return new BedrockOreDefinition(id, englishName, weight, size, minimumYield, maximumYield,
                depletionAmount, depletionChance, depletedYield, ores, dimensions, biomeWeightModifiers);
    }

    @SuppressWarnings("unused")
    private static WeightedOreResource ore(String resourceId, int weight) {
        return new WeightedOreResource(resourceId, weight);
    }

    @SuppressWarnings("unused")
    private static Set<ResourceKey<Level>> earthLike() {
        return Set.of(Level.OVERWORLD, GTDimensions.EARTH);
    }

    private static void validateOreResources() {
        for (BedrockOreDefinition definition : OVERWORLD) {
            for (WeightedOreResource ore : definition.ores()) {
                if (NaturalResourceList.byId(ore.resourceId()).isEmpty()) {
                    throw new IllegalStateException("Bedrock ore vein " + definition.id()
                            + " references unknown natural resource: " + ore.resourceId());
                }
            }
        }
    }
}
