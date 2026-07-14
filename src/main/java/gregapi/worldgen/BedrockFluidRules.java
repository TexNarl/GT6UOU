package gregapi.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Set;

public final class BedrockFluidRules {
    private BedrockFluidRules() {
    }

    public record BedrockFluidDefinition(
            String id,
            String englishName,
            ResourceLocation fluidId,
            int weight,
            int minimumYield,
            int maximumYield,
            int depletionAmount,
            int depletionChance,
            int depletedYield,
            Set<ResourceKey<Level>> dimensions,
            List<BiomeWeightModifier> biomeWeightModifiers
    ) {
        public BedrockFluidDefinition {
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

    public record BiomeWeightModifier(int addedWeight, TagKey<Biome> biomeTag) {
        public int weightFor(Holder<Biome> biome) {
            return biome.is(biomeTag) ? addedWeight : 0;
        }
    }
}
