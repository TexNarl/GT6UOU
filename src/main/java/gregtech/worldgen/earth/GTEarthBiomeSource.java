package gregtech.worldgen.earth;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import gregtech.worldgen.earth.climate.EarthBiomeSemantics;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stable Earth biome-source codec.
 *
 * <p>Until the regional layer exists this shell delegates to the vanilla
 * Overworld multi-noise source supplied in JSON. Later milestones replace the
 * sampling implementation without changing the Earth generator id.</p>
 */
public final class GTEarthBiomeSource extends BiomeSource {
    public static final MapCodec<GTEarthBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("delegate").forGetter(GTEarthBiomeSource::delegate)
    ).apply(instance, GTEarthBiomeSource::new));

    private final BiomeSource delegate;
    private volatile Map<net.minecraft.resources.ResourceKey<Biome>, Holder<Biome>> vanillaBiomes = Map.of();
    private volatile boolean vanillaBiomesResolved;
    @Nullable
    private volatile EarthGeneratorState earthState;

    public GTEarthBiomeSource(BiomeSource delegate) {
        this.delegate = delegate;
    }

    public BiomeSource delegate() {
        return delegate;
    }

    public GTEarthBiomeSource copyForLevel() {
        return new GTEarthBiomeSource(delegate);
    }

    public void initialize(EarthGeneratorState state) {
        earthState = state;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return delegate.possibleBiomes().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, @Nullable Climate.Sampler sampler) {
        EarthGeneratorState state = earthState;
        if (state == null) {
            return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }

        int blockX = quartX << 2;
        int blockZ = quartZ << 2;
        var profile = state.terrainProfiles().profileAtBlock(blockX, blockZ);
        var climate = state.climateModel().sample(blockX, blockZ);
        var riverInfo = state.riverSampler().sample(blockX, blockZ);
        boolean river = riverInfo != null && riverInfo.normalizedDistanceSq() <= 1.0;
        boolean karst = state.isKarstAt(blockX, blockZ);
        var key = EarthBiomeSemantics.resolve(
                profile,
                climate.averageTemperature(),
                climate.averageRainfall(),
                river,
                karst
        );
        Holder<Biome> biome = resolveVanillaBiomes().get(key);
        return biome != null ? biome : delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    /**
     * Resolve holders only after datapack registries have finished binding.
     * Calling {@link BiomeSource#possibleBiomes()} in the codec constructor is
     * too early for preset-backed multi-noise sources and crashes dedicated
     * server registry loading with an unbound-holder error.
     */
    private Map<net.minecraft.resources.ResourceKey<Biome>, Holder<Biome>> resolveVanillaBiomes() {
        if (!vanillaBiomesResolved) {
            synchronized (this) {
                if (!vanillaBiomesResolved) {
                    vanillaBiomes = delegate.possibleBiomes().stream()
                            .filter(holder -> holder.unwrapKey().isPresent())
                            .collect(Collectors.toUnmodifiableMap(
                                    holder -> holder.unwrapKey().orElseThrow(),
                                    Function.identity(),
                                    (first, ignored) -> first
                            ));
                    vanillaBiomesResolved = true;
                }
            }
        }
        return vanillaBiomes;
    }
}
