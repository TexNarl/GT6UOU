package gregapi.data;

import gregapi.worldgen.BedrockFluidRules.BedrockFluidDefinition;
import gregapi.worldgen.BedrockFluidRules.BiomeWeightModifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BedrockFluidList {
    public static final List<BedrockFluidDefinition> OVERWORLD = List.of(
            bedrockFluid("heavy_oil_deposit", "Heavy Oil Deposit", fluid("heavy_oil"),
                    15, 100, 200, 1, 100, 20,
                    earthLike(),
                    List.of(
                            new BiomeWeightModifier(5, BiomeTags.IS_OCEAN),
                            new BiomeWeightModifier(10, BiomeTags.IS_BEACH)
                    )),
            bedrockFluid("light_oil_deposit", "Light Oil Deposit", fluid("light_oil"),
                    25, 175, 300, 1, 100, 25, earthLike(), List.of()),
            bedrockFluid("natural_gas_deposit", "Natural Gas Deposit", fluid("natural_gas"),
                    15, 100, 175, 1, 100, 20, earthLike(), List.of()),
            bedrockFluid("oil_deposit", "Oil Deposit", fluid("oil"),
                    20, 175, 300, 1, 100, 25,
                    earthLike(),
                    List.of(
                            new BiomeWeightModifier(5, BiomeTags.IS_OCEAN),
                            new BiomeWeightModifier(5, BiomeTags.IS_BEACH)
                    )),
            bedrockFluid("raw_oil_deposit", "Raw Oil Deposit", fluid("raw_oil"),
                    20, 200, 300, 1, 100, 25, earthLike(), List.of()),
            bedrockFluid("salt_water_deposit", "Salt Water Deposit", fluid("salt_water"),
                    0, 50, 100, 1, 100, 15,
                    earthLike(),
                    List.of(
                            new BiomeWeightModifier(200, BiomeTags.IS_DEEP_OCEAN),
                            new BiomeWeightModifier(150, BiomeTags.IS_OCEAN)
                    ))
    );

    public static final Map<String, BedrockFluidDefinition> BY_ID = OVERWORLD.stream()
            .collect(Collectors.toUnmodifiableMap(BedrockFluidDefinition::id, Function.identity()));

    private BedrockFluidList() {
    }

    public static BedrockFluidDefinition byId(String id) {
        return BY_ID.get(id);
    }

    private static BedrockFluidDefinition bedrockFluid(
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
        return new BedrockFluidDefinition(id, englishName, fluidId, weight, minimumYield, maximumYield,
                depletionAmount, depletionChance, depletedYield, dimensions, biomeWeightModifiers);
    }

    private static ResourceLocation fluid(String id) {
        return ResourceLocation.fromNamespaceAndPath("gt6uou", id);
    }

    private static Set<ResourceKey<Level>> earthLike() {
        return Set.of(Level.OVERWORLD, DimensionList.EARTH);
    }
}
