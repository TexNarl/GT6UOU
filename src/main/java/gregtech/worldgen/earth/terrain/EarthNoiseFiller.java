/*
 * The separate height -> density -> surface pipeline follows TerraFirmaCraft's
 * TFCChunkGenerator and ChunkNoiseFiller architecture. TerraFirmaCraft and
 * TerraFirmaGreg are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.terrain;

import gregapi.data.StoneLayerList;
import gregtech.registry.GTBlocks;
import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import gregtech.worldgen.geology.StoneLayerStackSampler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the shared Earth surface-height field into physical chunk density.
 *
 * <p>Unlike the Overworld replacement path, Earth writes its final GT6UOU host
 * rocks directly. Its underground pass then evaluates TFC-style interpolated
 * cave density and a separate aquifer before the surface skin is applied.</p>
 */
public final class EarthNoiseFiller {
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    private final EarthGeneratorState state;
    private final StoneLayerStackSampler stoneLayers;
    private final Map<String, BlockState> rockStates;

    public EarthNoiseFiller(EarthGeneratorState state, StoneLayerStackSampler stoneLayers) {
        this.state = state;
        this.stoneLayers = stoneLayers;
        this.rockStates = findRockStates();
    }

    /** Samples all horizontal inputs once so density and surface cannot drift. */
    public ChunkTerrain prepare(ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        ChunkTerrain terrain = new ChunkTerrain(minY, maxY);

        for (int localZ = 0; localZ < 16; localZ++) {
            int z = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int x = minX + localX;
                int index = index(localX, localZ);
                TerrainProfile profile = state.terrainProfiles().profileAtBlock(x, z);
                EarthHeightFiller.ColumnSample column = state.heightFiller().sampleColumn(x, z);
                var climate = state.climateModel().sample(x, z);
                boolean river = column.riverCore();
                int surfaceY = Math.clamp(
                        (int) Math.floor(column.height()),
                        minY + 1,
                        maxY - 1
                );
                /*
                 * TFC/TFG's sea-level aquifer fills every terrain column below
                 * sea level. Restricting this to a semantic ocean weight left
                 * dry one-block seams wherever a low land/shore interpolation
                 * happened to have zero ocean weight.
                 */
                int fluidY = surfaceY < EarthTerrainProfiles.SEA_LEVEL
                        ? Math.min(EarthTerrainProfiles.SEA_LEVEL, maxY - 1)
                        : Integer.MIN_VALUE;

                terrain.surfaceY[index] = surfaceY;
                terrain.fluidY[index] = fluidY;
                terrain.profiles[index] = profile;
                terrain.river[index] = river;
                terrain.temperature[index] = climate.averageTemperature();
                terrain.rainfall[index] = climate.averageRainfall();
                terrain.karst[index] = state.isKarstAt(x, z);
                terrain.maxSurfaceY = Math.max(terrain.maxSurfaceY, surfaceY);
            }
        }
        return terrain;
    }

    /** Writes bedrock, final host rocks, caves, aquifers and surface water. */
    public void fill(
            ChunkAccess chunk,
            ChunkTerrain terrain,
            EarthUndergroundSystem.ChunkSampler underground
    ) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        CarvingMask carvingMask = chunk instanceof ProtoChunk protoChunk
                ? protoChunk.getOrCreateCarvingMask(GenerationStep.Carving.AIR)
                : null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < 16; localZ++) {
            int z = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int x = minX + localX;
                int index = index(localX, localZ);
                int surfaceY = terrain.surfaceY[index];
                int bedrockTop = terrain.minY + bedrockDepth(x, z) - 1;

                for (int y = terrain.minY; y <= surfaceY; y++) {
                    BlockState block;
                    if (y <= bedrockTop) {
                        block = BEDROCK;
                    } else {
                        double density = underground.density(localX, y, localZ, surfaceY);
                        BlockState substance = underground.substance(x, y, z, density);
                        if (substance == null) {
                            block = rockState(x, y, z, surfaceY);
                        } else if (substance.isAir()) {
                            block = Blocks.CAVE_AIR.defaultBlockState();
                        } else {
                            block = substance;
                            if (underground.shouldScheduleFluidUpdate()) {
                                cursor.set(x, y, z);
                                chunk.markPosForPostprocessing(cursor);
                            }
                        }
                        if (substance != null && carvingMask != null) {
                            carvingMask.set(x, y, z);
                        }
                    }
                    setBlock(chunk, localX, y, localZ, block);
                }

                int fluidY = terrain.fluidY[index];
                for (int y = surfaceY + 1; y <= fluidY; y++) {
                    setBlock(chunk, localX, y, localZ, WATER);
                }
            }
        }
    }

    private BlockState rockState(int x, int y, int z, int surfaceY) {
        String rockId = stoneLayers.sampleRockId(x, y, z, surfaceY);
        BlockState rock = rockStates.get(rockId);
        if (rock == null) {
            throw new IllegalStateException("Missing GT6UOU Earth host rock: " + rockId);
        }
        return rock;
    }

    private static void setBlock(
            ChunkAccess chunk,
            int localX,
            int y,
            int localZ,
            BlockState state
    ) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(localX, y & 15, localZ, state, false);
    }

    private static int bedrockDepth(int x, int z) {
        long value = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return 1 + Math.floorMod((int) value, 5);
    }

    private static Map<String, BlockState> findRockStates() {
        Map<String, BlockState> states = new LinkedHashMap<>();
        for (var layer : StoneLayerList.OVERWORLD_LAYERS) {
            Block block = GTBlocks.ROCK_BLOCKS.entrySet().stream()
                    .filter(entry -> layer.rockId().equals(entry.getKey().id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing gt6uou:" + layer.rockId() + " rock block"
                    ))
                    .getValue()
                    .get();
            states.put(layer.rockId(), block.defaultBlockState());
        }
        return Map.copyOf(states);
    }

    private static int index(int localX, int localZ) {
        return localX | localZ << 4;
    }

    /** Immutable-after-prepare column data shared with the surface pass. */
    public static final class ChunkTerrain {
        private final int minY;
        private final int maxY;
        private final int[] surfaceY = new int[16 * 16];
        private final int[] fluidY = new int[16 * 16];
        private final TerrainProfile[] profiles = new TerrainProfile[16 * 16];
        private final boolean[] river = new boolean[16 * 16];
        private final float[] temperature = new float[16 * 16];
        private final float[] rainfall = new float[16 * 16];
        private final boolean[] karst = new boolean[16 * 16];
        private int maxSurfaceY = Integer.MIN_VALUE;

        private ChunkTerrain(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        public int minY() {
            return minY;
        }

        public int maxY() {
            return maxY;
        }

        public int maxSurfaceY() {
            return maxSurfaceY;
        }

        public int surfaceY(int localX, int localZ) {
            return surfaceY[index(localX, localZ)];
        }

        public int fluidY(int localX, int localZ) {
            return fluidY[index(localX, localZ)];
        }

        public TerrainProfile profile(int localX, int localZ) {
            return profiles[index(localX, localZ)];
        }

        public boolean river(int localX, int localZ) {
            return river[index(localX, localZ)];
        }

        public float temperature(int localX, int localZ) {
            return temperature[index(localX, localZ)];
        }

        public float rainfall(int localX, int localZ) {
            return rainfall[index(localX, localZ)];
        }

        public boolean karst(int localX, int localZ) {
            return karst[index(localX, localZ)];
        }
    }
}
