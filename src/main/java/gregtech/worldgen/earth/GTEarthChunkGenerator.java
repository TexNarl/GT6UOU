package gregtech.worldgen.earth;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import gregtech.GT6UOU;
import gregtech.worldgen.earth.terrain.EarthNoiseFiller;
import gregtech.worldgen.earth.terrain.EarthTerrainProfiles;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Serialized entry point for GT6UOU Earth.
 *
 * <p>Height queries, physical density, fluids and surface construction all use
 * the same seed-bound TFG-style Earth state. The custom path exists only on
 * {@code gt6uou:earth}; the Overworld generator is untouched.</p>
 */
public final class GTEarthChunkGenerator extends NoiseBasedChunkGenerator {
    private static final List<ResourceKey<ConfiguredWorldCarver<?>>> EARTH_CARVERS = List.of(
            configuredCarverKey("earth_cave"),
            configuredCarverKey("earth_deep_cave"),
            configuredCarverKey("earth_canyon")
    );

    public static final MapCodec<GTEarthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(GTEarthChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(GTEarthChunkGenerator::noiseSettings),
            GTEarthWorldgen.Settings.CODEC.fieldOf("earth_settings").forGetter(GTEarthChunkGenerator::earthSettings)
    ).apply(instance, GTEarthChunkGenerator::new));

    private final Holder<NoiseGeneratorSettings> noiseSettings;
    private final GTEarthWorldgen.Settings earthSettings;

    @Nullable
    private volatile EarthGeneratorState earthState;

    public GTEarthChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> noiseSettings,
            GTEarthWorldgen.Settings earthSettings
    ) {
        super(biomeSource, noiseSettings);
        this.noiseSettings = noiseSettings;
        this.earthSettings = earthSettings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public Holder<NoiseGeneratorSettings> noiseSettings() {
        return noiseSettings;
    }

    public GTEarthWorldgen.Settings earthSettings() {
        return earthSettings;
    }

    public synchronized EarthGeneratorState initializeForLevel(ServerLevel level) {
        if (earthState != null) {
            if (!earthState.matches(level.dimension(), level.getSeed())) {
                throw new IllegalStateException("Earth generator instance was reused across different levels");
            }
            return earthState;
        }

        earthState = EarthGeneratorState.create(level.dimension(), level.getSeed());
        if (getBiomeSource() instanceof GTEarthBiomeSource earthBiomeSource) {
            earthBiomeSource.initialize(earthState);
        }
        GT6UOU.LOGGER.info(
                "Initialized GT6UOU Earth state: dimension={}, seed={}, fingerprint={}",
                level.dimension().location(),
                level.getSeed(),
                Long.toUnsignedString(earthState.fingerprint(), 16)
        );
        return earthState;
    }

    @Nullable
    public EarthGeneratorState earthState() {
        return earthState;
    }

    public GTEarthChunkGenerator copyForLevel() {
        BiomeSource source = getBiomeSource() instanceof GTEarthBiomeSource earthBiomeSource
                ? earthBiomeSource.copyForLevel()
                : getBiomeSource();
        return new GTEarthChunkGenerator(source, noiseSettings, earthSettings);
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types type,
            LevelHeightAccessor level,
            RandomState randomState
    ) {
        EarthGeneratorState state = ((EarthRandomStateAccess) (Object) randomState).gt6uou$getEarthState();
        if (state == null) {
            state = earthState;
        }
        if (state == null) {
            return super.getBaseHeight(x, z, type, level, randomState);
        }

        var column = state.heightFiller().sampleColumn(x, z);
        int surfaceY = (int) Math.floor(column.height());
        if (type == Heightmap.Types.WORLD_SURFACE
                || type == Heightmap.Types.WORLD_SURFACE_WG
                || type == Heightmap.Types.MOTION_BLOCKING
                || type == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) {
            boolean water = surfaceY < EarthTerrainProfiles.SEA_LEVEL;
            return (water ? Math.max(surfaceY, EarthTerrainProfiles.SEA_LEVEL) : surfaceY) + 1;
        }
        return surfaceY + 1;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk
    ) {
        EarthGeneratorState state = resolveState(randomState);
        if (state == null) {
            return super.fillFromNoise(blender, randomState, structureManager, chunk);
        }

        LevelChunkSection[] sections = chunk.getSections();
        for (LevelChunkSection section : sections) {
            section.acquire();
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                EarthNoiseFiller.ChunkTerrain terrain = state.noiseFiller().prepare(chunk);
                var underground = state.undergroundSystem(randomState).prepare(chunk, terrain);
                state.noiseFiller().fill(chunk, terrain, underground);
                state.surfaceManager().buildSurface(chunk, terrain);
                Heightmap.primeHeightmaps(chunk, EnumSet.of(
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        Heightmap.Types.MOTION_BLOCKING,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                ));
                return chunk;
            } finally {
                for (LevelChunkSection section : sections) {
                    section.release();
                }
            }
        }, Util.backgroundExecutor());
    }

    /** Surface blocks are already applied after Earth density filling. */
    @Override
    public void buildSurface(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk
    ) {
    }

    /**
     * TFC/TFG performs only the AIR pass, scans a 17x17 origin-chunk window,
     * and lets all cave/canyon shapes share the density filler's aquifer.
     * Earth mirrors that architecture with its own fixed configured carvers;
     * inherited vanilla-biome carvers remain disabled.
     */
    @Override
    public void applyCarvers(
            WorldGenRegion level,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving carvingStep
    ) {
        if (carvingStep != GenerationStep.Carving.AIR) {
            return;
        }

        EarthGeneratorState state = resolveState(randomState);
        if (state == null) {
            return;
        }

        var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_CARVER);
        var carvers = EARTH_CARVERS.stream()
                .map(key -> registry.getHolderOrThrow(key).value())
                .toList();
        CarvingContext context = new CarvingContext(
                this,
                level.registryAccess(),
                chunk.getHeightAccessorForGeneration(),
                null,
                randomState,
                noiseSettings.value().surfaceRule()
        );
        var carvingMask = ((ProtoChunk) chunk).getOrCreateCarvingMask(carvingStep);
        PositionalRandomFactory randomFactory = new XoroshiroRandomSource(seed).forkPositional();
        ChunkPos target = chunk.getPos();
        Aquifer aquifer = state.undergroundSystem(randomState).aquifer(chunk);

        for (int offsetX = -8; offsetX <= 8; offsetX++) {
            for (int offsetZ = -8; offsetZ <= 8; offsetZ++) {
                ChunkPos origin = new ChunkPos(target.x + offsetX, target.z + offsetZ);
                int carverIndex = 1;
                for (ConfiguredWorldCarver<?> carver : carvers) {
                    RandomSource random = randomFactory.at(origin.x, carverIndex, origin.z);
                    if (carver.isStartChunk(random)) {
                        carver.carve(
                                context,
                                chunk,
                                biomeManager::getBiome,
                                random,
                                aquifer,
                                origin,
                                carvingMask
                        );
                    }
                    carverIndex++;
                }
            }
        }
        state.surfaceManager().repairOceanWaterSurface(chunk);
        state.surfaceManager().buildCaveSurfaces(chunk);
    }

    @Override
    public int getSeaLevel() {
        return EarthTerrainProfiles.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return noiseSettings.value().noiseSettings().minY();
    }

    @Override
    public int getGenDepth() {
        return noiseSettings.value().noiseSettings().height();
    }

    /** TFC likewise leaves this compatibility query empty for its custom fill. */
    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            LevelHeightAccessor level,
            RandomState randomState
    ) {
        return new NoiseColumn(0, new BlockState[0]);
    }

    @Nullable
    private EarthGeneratorState resolveState(RandomState randomState) {
        EarthGeneratorState state = ((EarthRandomStateAccess) (Object) randomState).gt6uou$getEarthState();
        return state != null ? state : earthState;
    }

    private static ResourceKey<ConfiguredWorldCarver<?>> configuredCarverKey(String path) {
        return ResourceKey.create(
                Registries.CONFIGURED_CARVER,
                ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, path)
        );
    }

}
