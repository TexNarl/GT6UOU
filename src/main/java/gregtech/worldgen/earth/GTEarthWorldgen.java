package gregtech.worldgen.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import gregtech.GT6UOU;
import gregtech.worldgen.earth.terrain.EarthCarvers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration and serialized settings for the GT6UOU Earth generator.
 *
 * <p>The format version is deliberately stored in the dimension JSON from the
 * first implementation milestone. Future world-generation settings can be
 * added as optional codec fields without silently changing old Earth worlds.</p>
 */
public final class GTEarthWorldgen {
    public static final int CURRENT_FORMAT_VERSION = 1;

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, GT6UOU.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, GT6UOU.MODID);

    static {
        CHUNK_GENERATORS.register("earth", () -> GTEarthChunkGenerator.CODEC);
        BIOME_SOURCES.register("earth", () -> GTEarthBiomeSource.CODEC);
    }

    private GTEarthWorldgen() {
    }

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
        EarthCarvers.register(modEventBus);
    }

    public record Settings(int formatVersion) {
        public static final Settings DEFAULT = new Settings(CURRENT_FORMAT_VERSION);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("format_version", CURRENT_FORMAT_VERSION)
                        .validate(Settings::validateFormatVersion)
                        .forGetter(Settings::formatVersion)
        ).apply(instance, Settings::new));

        private static DataResult<Integer> validateFormatVersion(int version) {
            if (version == CURRENT_FORMAT_VERSION) {
                return DataResult.success(version);
            }
            return DataResult.error(() -> "Unsupported GT6UOU Earth settings format " + version
                    + "; expected " + CURRENT_FORMAT_VERSION);
        }
    }
}
