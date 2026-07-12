package gregapi.data;

import gregapi.worldgen.BedrockFluidRules.BedrockFluidDefinition;
import gregapi.worldgen.BedrockFluidRules.BiomeWeightModifier;
import gregtech.registry.GTFluids;
import gregtech.worldgen.GTDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class BedrockFluidList {
    public static final List<BedrockFluidDefinition> OVERWORLD = List.of(
            bedrockFluid("heavy_oil_deposit", "Heavy Oil Deposit", GTFluids.HEAVY_OIL,
                    15, 100, 200, 1, 100, 20,
                    earthLike(),
                    List.of(
                            new BiomeWeightModifier(5, BiomeTags.IS_OCEAN),
                            new BiomeWeightModifier(10, BiomeTags.IS_BEACH)
                    )),
            bedrockFluid("light_oil_deposit", "Light Oil Deposit", GTFluids.LIGHT_OIL,
                    25, 175, 300, 1, 100, 25, earthLike(), List.of()),
            bedrockFluid("natural_gas_deposit", "Natural Gas Deposit", GTFluids.NATURAL_GAS,
                    15, 100, 175, 1, 100, 20, earthLike(), List.of()),
            bedrockFluid("oil_deposit", "Oil Deposit", GTFluids.OIL,
                    20, 175, 300, 1, 100, 25,
                    earthLike(),
                    List.of(
                            new BiomeWeightModifier(5, BiomeTags.IS_OCEAN),
                            new BiomeWeightModifier(5, BiomeTags.IS_BEACH)
                    )),
            bedrockFluid("raw_oil_deposit", "Raw Oil Deposit", GTFluids.RAW_OIL,
                    20, 200, 300, 1, 100, 25, earthLike(), List.of()),
            bedrockFluid("salt_water_deposit", "Salt Water Deposit", GTFluids.SALT_WATER,
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
            Supplier<? extends Fluid> fluid,
            int weight,
            int minimumYield,
            int maximumYield,
            int depletionAmount,
            int depletionChance,
            int depletedYield,
            Set<ResourceKey<Level>> dimensions,
            List<BiomeWeightModifier> biomeWeightModifiers
    ) {
        return new BedrockFluidDefinition(id, englishName, fluid, weight, minimumYield, maximumYield,
                depletionAmount, depletionChance, depletedYield, dimensions, biomeWeightModifiers);
    }

    private static Set<ResourceKey<Level>> earthLike() {
        return Set.of(Level.OVERWORLD, GTDimensions.EARTH);
    }
}
