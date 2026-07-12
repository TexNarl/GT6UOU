package gregapi.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BedrockOreRules {
    private BedrockOreRules() {
    }

    /**
     * API-level description of an abstract bedrock ore vein.
     *
     * <p>Bedrock ore veins do not place ore blocks in the world. They are
     * invisible chunk resources intended for future bedrock miners/drills. A
     * vein stores its possible ore materials, size in chunks, yield and
     * depletion behavior.</p>
     */
    public record BedrockOreDefinition(
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
        public BedrockOreDefinition {
            id = normalizeId(id);
            englishName = requireText(englishName, "englishName");
            if (weight < 0) {
                throw new IllegalArgumentException("Bedrock ore vein weight cannot be negative: " + id);
            }
            if (size < 1) {
                throw new IllegalArgumentException("Bedrock ore vein size must be at least 1 chunk: " + id);
            }
            if (minimumYield < 0 || maximumYield < minimumYield) {
                throw new IllegalArgumentException("Invalid bedrock ore yield range: " + id);
            }
            if (depletionAmount < 0) {
                throw new IllegalArgumentException("Bedrock ore depletion amount cannot be negative: " + id);
            }
            if (depletionChance < 0 || depletionChance > 100) {
                throw new IllegalArgumentException("Bedrock ore depletion chance must be 0..100: " + id);
            }
            if (depletedYield < 0) {
                throw new IllegalArgumentException("Bedrock ore depleted yield cannot be negative: " + id);
            }
            ores = List.copyOf(ores);
            dimensions = Set.copyOf(dimensions);
            biomeWeightModifiers = List.copyOf(biomeWeightModifiers);
        }

        public boolean isAllowedIn(ResourceKey<Level> dimension) {
            return dimensions.isEmpty() || dimensions.contains(dimension);
        }

        public int weightFor(Holder<Biome> biome) {
            int result = weight;

            for (BiomeWeightModifier modifier : biomeWeightModifiers) {
                result += modifier.weightFor(biome);
            }

            return Math.max(0, result);
        }
    }

    public record WeightedOreResource(String resourceId, int weight) {
        public WeightedOreResource {
            resourceId = normalizeId(resourceId);
            if (weight <= 0) {
                throw new IllegalArgumentException("Bedrock ore resource weight must be positive: " + resourceId);
            }
        }
    }

    public record BiomeWeightModifier(int addedWeight, TagKey<Biome> biomeTag) {
        public int weightFor(Holder<Biome> biome) {
            return biome.is(biomeTag) ? addedWeight : 0;
        }
    }

    private static String normalizeId(String id) {
        String normalized = requireText(id, "id").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Bedrock ore id must use only lowercase letters, numbers and underscores: " + id);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Bedrock ore " + field + " cannot be blank");
        }
        return value;
    }
}
