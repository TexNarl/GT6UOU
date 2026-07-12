package gregtech.worldgen;

import gregapi.code.MineralResource;
import gregapi.code.OreBlockForm;
import gregapi.data.VeinList;
import gregapi.worldgen.OreVeinRules.OreVeinDefinition;
import gregapi.worldgen.OreVeinRules.OreVeinOreEntry;
import gregapi.worldgen.OreVeinRules.OreVeinOreRole;
import gregapi.worldgen.OreVeinRules.OreVeinSize;
import gregapi.worldgen.OreVeinRules.VeinShapeTypes;
import gregtech.registry.GTBlocks;
import gregtech.worldgen.sampler.StoneLayerStackSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * First practical ore-vein placement pass.
 *
 * <p>The API data in {@link VeinList} describes GTM-like vein types and
 * which GT6UOU stone layers can host them. This implementation places only
 * Overworld veins, replaces only matching host-rock blocks, and can select
 * normal, rich, poor and compact raw ore block forms for resources that have
 * those registered forms. Host-rock specific ore blocks/textures can be layered
 * on top later without changing the vein selection rules.</p>
 */
public final class OreVeinWorldgen {
    private static final int VEIN_RANDOM_OFFSET_BLOCKS = 24;
    private static final int VEIN_SEARCH_DISTANCE_CHUNKS = 5;
    private static final int GIANT_VEIN_SEARCH_DISTANCE_CHUNKS = 96;
    private static final int GIANT_VEIN_ORIGIN_STEP_CHUNKS = 16;
    private static final int CENTER_SEARCH_ATTEMPTS = 16;
    private static final int CENTER_HOST_ROCK_MARGIN_BLOCKS = 20;
    private static final int CENTER_VERTICAL_HOST_MARGIN_BLOCKS = 20;
    private static final long SEED_SALT = 0x4F52455645494E31L; // "OREVEIN1"
    private static final long RAW_BLOCK_SALT = 0x524157424C4F434BL; // "RAWBLOCK"
    private static final long RICH_ORE_SALT = 0x524943484F524531L; // "RICHORE1"
    private static final long POOR_ORE_SALT = 0x504F4F524F524531L; // "POORORE1"
    private static final double RAW_BLOCK_CHANCE = 0.01;
    private static final double MAX_RICH_ORE_CHANCE = 0.15;
    private static final double MAX_POOR_ORE_CHANCE = 0.05;
    private static final double RICH_ORE_EDGE_BEGIN = 0.70;
    private static final double POOR_ORE_EDGE_END = 0.30;
    private static final int SURFACE_RAW_MARKER_MIN = 3;
    private static final int SURFACE_RAW_MARKER_MAX = 5;
    private static final int SURFACE_RAW_MARKER_ATTEMPTS = 128;

    private OreVeinWorldgen() {
    }

    public static int generateOverworldOreVeins(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        if (!GTDimensions.isEarthLike(level)) {
            return 0;
        }

        ResourceBlockStates resourceStates = findResourceStates();
        Map<String, Block> hostRockBlocks = findHostRockBlocks();
        if (resourceStates.isEmpty() || hostRockBlocks.isEmpty()) {
            return 0;
        }

        int replacedBlocks = 0;
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int originChunkX = chunkX - VEIN_SEARCH_DISTANCE_CHUNKS; originChunkX <= chunkX + VEIN_SEARCH_DISTANCE_CHUNKS; originChunkX++) {
            for (int originChunkZ = chunkZ - VEIN_SEARCH_DISTANCE_CHUNKS; originChunkZ <= chunkZ + VEIN_SEARCH_DISTANCE_CHUNKS; originChunkZ++) {
                for (OreVeinDefinition definition : VeinList.OVERWORLD) {
                    if (isGiantVein(definition) || !passesVeinRarity(level.getSeed(), definition, originChunkX, originChunkZ)) {
                        continue;
                    }

                    OreVeinInstance vein = createVein(level, definition, resourceStates, originChunkX, originChunkZ, minY, maxY);
                    if (vein == null) {
                        continue;
                    }

                    if (!vein.intersectsChunk(minX, minZ, minY, maxY)) {
                        continue;
                    }

                    int placedInSlice = placeVeinSlice(chunk, pos, minX, minZ, minY, maxY, vein, resourceStates, hostRockBlocks);
                    replacedBlocks += placedInSlice;

                    if (placedInSlice > 0) {
                        replacedBlocks += placeSurfaceRawMarkers(level, chunk, pos, minX, minZ, minY, maxY, vein, resourceStates);
                    }
                }
            }
        }

        replacedBlocks += generateGiantVeinSlices(level, chunk, pos, minX, minZ, minY, maxY, resourceStates, hostRockBlocks);

        return replacedBlocks;
    }

    private static int generateGiantVeinSlices(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int minY,
            int maxY,
            ResourceBlockStates resourceStates,
            Map<String, Block> hostRockBlocks
    ) {
        int replacedBlocks = 0;
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int firstOriginChunkX = Math.floorDiv(chunkX - GIANT_VEIN_SEARCH_DISTANCE_CHUNKS, GIANT_VEIN_ORIGIN_STEP_CHUNKS) * GIANT_VEIN_ORIGIN_STEP_CHUNKS;
        int firstOriginChunkZ = Math.floorDiv(chunkZ - GIANT_VEIN_SEARCH_DISTANCE_CHUNKS, GIANT_VEIN_ORIGIN_STEP_CHUNKS) * GIANT_VEIN_ORIGIN_STEP_CHUNKS;
        int lastOriginChunkX = Math.floorDiv(chunkX + GIANT_VEIN_SEARCH_DISTANCE_CHUNKS, GIANT_VEIN_ORIGIN_STEP_CHUNKS) * GIANT_VEIN_ORIGIN_STEP_CHUNKS;
        int lastOriginChunkZ = Math.floorDiv(chunkZ + GIANT_VEIN_SEARCH_DISTANCE_CHUNKS, GIANT_VEIN_ORIGIN_STEP_CHUNKS) * GIANT_VEIN_ORIGIN_STEP_CHUNKS;

        for (int originChunkX = firstOriginChunkX; originChunkX <= lastOriginChunkX; originChunkX += GIANT_VEIN_ORIGIN_STEP_CHUNKS) {
            for (int originChunkZ = firstOriginChunkZ; originChunkZ <= lastOriginChunkZ; originChunkZ += GIANT_VEIN_ORIGIN_STEP_CHUNKS) {
                for (OreVeinDefinition definition : VeinList.OVERWORLD) {
                    if (!isGiantVein(definition) || !passesVeinRarity(level.getSeed(), definition, originChunkX, originChunkZ)) {
                        continue;
                    }

                    OreVeinInstance vein = createVein(level, definition, resourceStates, originChunkX, originChunkZ, minY, maxY);
                    if (vein == null) {
                        continue;
                    }

                    if (!vein.intersectsChunk(minX, minZ, minY, maxY)) {
                        continue;
                    }

                    int placedInSlice = placeVeinSlice(chunk, pos, minX, minZ, minY, maxY, vein, resourceStates, hostRockBlocks);
                    replacedBlocks += placedInSlice;

                    if (placedInSlice > 0) {
                        replacedBlocks += placeSurfaceRawMarkers(level, chunk, pos, minX, minZ, minY, maxY, vein, resourceStates);
                    }
                }
            }
        }

        return replacedBlocks;
    }

    private static boolean isGiantVein(OreVeinDefinition definition) {
        return definition.id().equals("oilsands_vein");
    }

    private static OreVeinInstance createVein(
            WorldGenLevel level,
            OreVeinDefinition definition,
            ResourceBlockStates resourceStates,
            int originChunkX,
            int originChunkZ,
            int minY,
            int maxY
    ) {
        long seed = level.getSeed() ^ SEED_SALT;
        int baseHorizontalSize = randomBetween(seed ^ 0x9E3779B97F4A7C15L, originChunkX, originChunkZ,
                definition.settings().clusterMin(), definition.settings().clusterMax());
        OreVeinSize sizeTier = selectSizeTier(seed, definition, originChunkX, originChunkZ);
        int horizontalSize = sizeTier.scaleSize(baseHorizontalSize);
        int radiusX = Math.max(6, horizontalSize / 2);
        int radiusZ = Math.max(6, randomBetween(seed ^ 0xC2B2AE3D27D4EB4FL, originChunkX, originChunkZ,
                Math.max(6, horizontalSize / 3), Math.max(8, horizontalSize / 2)));
        int radiusY = verticalRadius(definition, horizontalSize);

        int angleStep = Math.floorMod(stableChunkHash(seed ^ 0x94D049BB133111EBL, originChunkX, originChunkZ), 360);
        double angle = Math.toRadians(angleStep);
        double tiltX = (Math.floorMod(stableChunkHash(seed ^ 0xD6E8FEB86659FD93L, originChunkX, originChunkZ), 2001) - 1000) / 1000.0;
        double tiltZ = (Math.floorMod(stableChunkHash(seed ^ 0xA5A3564E27F886D1L, originChunkX, originChunkZ), 2001) - 1000) / 1000.0;

        StoneLayerStackSampler sampler = new StoneLayerStackSampler(level.getSeed());

        for (int attempt = 0; attempt < CENTER_SEARCH_ATTEMPTS; attempt++) {
            long attemptSeed = seed ^ ((long) attempt * 0x9E3779B97F4A7C15L);
            int centerX = (originChunkX << 4) + 8
                    + randomBetween(attemptSeed ^ 0x165667B19E3779F9L, originChunkX, originChunkZ, -VEIN_RANDOM_OFFSET_BLOCKS, VEIN_RANDOM_OFFSET_BLOCKS);
            int centerZ = (originChunkZ << 4) + 8
                    + randomBetween(attemptSeed ^ 0x85EBCA77C2B2AE63L, originChunkX, originChunkZ, -VEIN_RANDOM_OFFSET_BLOCKS, VEIN_RANDOM_OFFSET_BLOCKS);
            VerticalHostRun hostRun = selectVerticalHostRun(
                    sampler,
                    definition,
                    centerX,
                    centerZ,
                    minY,
                    maxY,
                    level.getSeaLevel(),
                    attemptSeed,
                    originChunkX,
                    originChunkZ
            );

            if (hostRun == null) {
                continue;
            }

            int centerY = randomBetween(
                    attemptSeed ^ 0x27D4EB2F165667C5L,
                    originChunkX,
                    originChunkZ,
                    hostRun.minCenterY(),
                    hostRun.maxCenterY()
            );

            if (!hasHorizontalHostRockMargin(sampler, hostRun.rockId(), centerX, centerY, centerZ, radiusX, radiusZ, level.getSeaLevel())) {
                continue;
            }

            OreVeinInstance vein = new OreVeinInstance(definition, resourceStates, hostRun.rockId(), originChunkX, originChunkZ, centerX, centerY, centerZ, radiusX, radiusY, radiusZ, angle, tiltX * 0.45, tiltZ * 0.45);
            return vein;
        }

        return null;
    }

    private static OreVeinSize selectSizeTier(long seed, OreVeinDefinition definition, int originChunkX, int originChunkZ) {
        int roll = Math.floorMod(stableChunkHash(seed ^ 0x53495A4554494552L ^ definition.id().hashCode(), originChunkX, originChunkZ), 100);

        if (roll < 20) {
            return OreVeinSize.SMALL;
        }
        if (roll < 80) {
            return OreVeinSize.MEDIUM;
        }
        return OreVeinSize.LARGE;
    }

    private static VerticalHostRun selectVerticalHostRun(
            StoneLayerStackSampler sampler,
            OreVeinDefinition definition,
            int centerX,
            int centerZ,
            int minY,
            int maxY,
            int seaLevel,
            long seed,
            int originChunkX,
            int originChunkZ
    ) {
        int lowerY = Math.max(minY + CENTER_VERTICAL_HOST_MARGIN_BLOCKS, minY + 8);
        int upperY = Math.min(maxY - CENTER_VERTICAL_HOST_MARGIN_BLOCKS - 1, Math.max(lowerY, seaLevel + 20));
        int surfaceY = estimatedSurfaceY(seaLevel);
        VerticalHostRun selected = null;
        int selectedWeight = 0;
        String currentRockId = null;
        int runStartY = lowerY;

        for (int y = lowerY; y <= upperY + 1; y++) {
            String rockId = y <= upperY ? sampler.sampleRockId(centerX, y, centerZ, surfaceY) : null;
            boolean validHost = rockId != null && definition.hostRockIds().contains(rockId);

            if (validHost && rockId.equals(currentRockId)) {
                continue;
            }

            if (currentRockId != null) {
                VerticalHostRun run = VerticalHostRun.of(currentRockId, runStartY, y - 1);

                if (run != null) {
                    int weight = run.length();
                    selectedWeight += weight;
                    int roll = Math.floorMod(stableChunkHash(seed ^ currentRockId.hashCode(), originChunkX, originChunkZ), selectedWeight);

                    if (roll < weight) {
                        selected = run;
                    }
                }
            }

            currentRockId = validHost ? rockId : null;
            runStartY = y;
        }

        return selected;
    }

    private static boolean hasHorizontalHostRockMargin(
            StoneLayerStackSampler sampler,
            String targetRockId,
            int centerX,
            int centerY,
            int centerZ,
            int radiusX,
            int radiusZ,
            int seaLevel
    ) {
        int margin = CENTER_HOST_ROCK_MARGIN_BLOCKS;
        int surfaceY = estimatedSurfaceY(seaLevel);
        int[][] offsets = {
                {margin, 0},
                {-margin, 0},
                {0, margin},
                {0, -margin},
                {margin, margin},
                {margin, -margin},
                {-margin, margin},
                {-margin, -margin}
        };

        if (!targetRockId.equals(sampler.sampleRockId(centerX, centerY, centerZ, surfaceY))) {
            return false;
        }

        for (int[] offset : offsets) {
            String rockId = sampler.sampleRockId(centerX + offset[0], centerY, centerZ + offset[1], surfaceY);

            if (!targetRockId.equals(rockId)) {
                return false;
            }
        }

        return true;
    }

    private static int estimatedSurfaceY(int seaLevel) {
        return seaLevel + 20;
    }

    private static boolean passesVeinRarity(long worldSeed, OreVeinDefinition definition, int originChunkX, int originChunkZ) {
        int rarity = effectiveRarity(definition);
        int roll = Math.floorMod(stableChunkHash(worldSeed ^ SEED_SALT ^ definition.id().hashCode(), originChunkX, originChunkZ), rarity);

        return roll == 0;
    }

    private static int effectiveRarity(OreVeinDefinition definition) {
        if (isGiantVein(definition)) {
            return 900;
        }

        int sourceWeight = Math.max(1, definition.settings().sourceGtmWeight());
        return Math.max(24, Math.round(480.0F / (float) Math.sqrt(sourceWeight)));
    }

    private static int verticalRadius(OreVeinDefinition definition, int horizontalSize) {
        VeinShapeTypes type = definition.shape().type();

        return switch (type) {
            case DIKE -> Math.max(14, horizontalSize / 2);
            case CUBOID -> Math.max(8, horizontalSize / 4);
            case CLASSIC_BANDED -> Math.max(10, horizontalSize / 3);
            case LAYERED, DISC -> Math.max(4, horizontalSize / 8);
            case PIPE -> Math.max(16, horizontalSize / 2);
            case GEODE -> Math.max(8, horizontalSize / 4);
            case MIXED_CLUSTER -> Math.max(8, horizontalSize / 4);
        };
    }

    private static int placeVeinSlice(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int minY,
            int maxY,
            OreVeinInstance vein,
            ResourceBlockStates resourceStates,
            Map<String, Block> hostRockBlocks
    ) {
        int replacedBlocks = 0;
        int localMinX = Math.max(0, vein.minX() - minX);
        int localMaxX = Math.min(15, vein.maxX() - minX);
        int localMinZ = Math.max(0, vein.minZ() - minZ);
        int localMaxZ = Math.min(15, vein.maxZ() - minZ);
        int veinMinY = Math.max(minY, vein.minY());
        int veinMaxY = Math.min(maxY - 1, vein.maxY());

        if (localMinX > localMaxX || localMinZ > localMaxZ || veinMinY > veinMaxY) {
            return 0;
        }

        for (int localX = localMinX; localX <= localMaxX; localX++) {
            int x = minX + localX;

            for (int localZ = localMinZ; localZ <= localMaxZ; localZ++) {
                int z = minZ + localZ;

                for (int y = veinMinY; y <= veinMaxY; y++) {
                    if (!vein.contains(x, y, z)) {
                        continue;
                    }

                    pos.set(x, y, z);
                    BlockState currentState = chunk.getBlockState(pos);

                    if (!canReplaceHostRock(currentState, vein.definition(), hostRockBlocks)) {
                        continue;
                    }

                    if (!passesDensity(vein, x, y, z)) {
                        continue;
                    }

                    OreVeinOreEntry entry = selectOreEntry(vein, x, y, z);
                    BlockState oreState = selectResourceState(vein, entry.oreId(), x, y, z);

                    if (oreState == null) {
                        continue;
                    }

                    chunk.setBlockState(pos, oreState, false);
                    replacedBlocks++;
                }
            }
        }

        return replacedBlocks;
    }

    private static int placeSurfaceRawMarkers(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int minX,
            int minZ,
            int minY,
            int maxY,
            OreVeinInstance vein,
            ResourceBlockStates resourceStates
    ) {
        List<BlockState> markerStates = rawMarkerStates(vein.definition(), resourceStates);
        if (markerStates.isEmpty()) {
            return 0;
        }

        int markerCount = isSurfaceMarkerChunk(chunk, vein)
                ? randomBetween(
                        vein.seed() ^ 0x5155524641434552L,
                        Math.floorDiv(vein.centerX(), 16),
                        Math.floorDiv(vein.centerZ(), 16),
                        SURFACE_RAW_MARKER_MIN,
                        SURFACE_RAW_MARKER_MAX
                )
                : 1;
        int placed = 0;
        long seed = vein.seed()
                ^ ((long) chunk.getPos().x * 0x9E3779B97F4A7C15L)
                ^ ((long) chunk.getPos().z * 0xC2B2AE3D27D4EB4FL)
                ^ 0x524157535552464CL;

        for (int attempt = 0; attempt < SURFACE_RAW_MARKER_ATTEMPTS && placed < markerCount; attempt++) {
            int localX = Math.floorMod(stableBlockHash(seed ^ 0x9E3779B97F4A7C15L, vein.centerX(), attempt, chunk.getPos().x), 16);
            int localZ = Math.floorMod(stableBlockHash(seed ^ 0xC2B2AE3D27D4EB4FL, vein.centerZ(), attempt, chunk.getPos().z), 16);
            int x = minX + localX;
            int z = minZ + localZ;
            int y = findSurfaceMarkerY(level, chunk, pos, x, z, minY, maxY);

            if (y == Integer.MIN_VALUE) {
                continue;
            }

            BlockState markerState = markerStates.get(Math.floorMod(stableBlockHash(seed, x, y, z), markerStates.size()));
            pos.set(x, y, z);
            chunk.setBlockState(pos, markerState, false);
            placed++;
        }

        return placed;
    }

    private static boolean isSurfaceMarkerChunk(ChunkAccess chunk, OreVeinInstance vein) {
        return chunk.getPos().x == vein.originChunkX()
                && chunk.getPos().z == vein.originChunkZ();
    }

    private static List<BlockState> rawMarkerStates(OreVeinDefinition definition, ResourceBlockStates resourceStates) {
        Map<String, BlockState> uniqueStates = new LinkedHashMap<>();
        for (String oreId : new LinkedHashSet<>(definition.oreIds())) {
            BlockState rawBlockState = resourceStates.get(oreId, OreBlockForm.RAW_BLOCK);

            if (rawBlockState != null) {
                uniqueStates.put(oreId, rawBlockState);
            }
        }

        return new ArrayList<>(uniqueStates.values());
    }

    private static int findSurfaceMarkerY(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            int minY,
            int maxY
    ) {
        int lowerY = Math.max(level.getMinBuildHeight() + 1, minY + 1);
        int upperY = Math.min(level.getMaxBuildHeight() - 1, maxY - 1);

        for (int y = upperY; y >= lowerY; y--) {
            pos.set(x, y, z);
            BlockState supportState = chunk.getBlockState(pos);

            if (!canSupportSurfaceMarker(level, pos, supportState)) {
                continue;
            }

            int markerY = y + 1;
            if (markerY >= level.getMaxBuildHeight()) {
                continue;
            }

            pos.set(x, markerY, z);
            if (canReplaceSurfaceMarker(chunk.getBlockState(pos))) {
                return markerY;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean canReplaceSurfaceMarker(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(BlockTags.FLOWERS);
    }

    private static boolean canSupportSurfaceMarker(WorldGenLevel level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty()
                && isNaturalSurfaceMarkerSupport(state)
                && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean isNaturalSurfaceMarkerSupport(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MUD)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.STONE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.DEEPSLATE)) {
            return true;
        }

        return GTBlocks.ROCK_BLOCKS.values().stream().anyMatch(block -> state.is(block.get()))
                || GTBlocks.CLAY_BLOCKS.values().stream().anyMatch(block -> state.is(block.get()))
                || GTBlocks.SAND_BLOCKS.values().stream().anyMatch(block -> state.is(block.get()))
                || GTBlocks.OTHER_BLOCKS.values().stream().anyMatch(block -> state.is(block.get()));
    }

    private static boolean canReplaceHostRock(BlockState state, OreVeinDefinition definition, Map<String, Block> hostRockBlocks) {
        for (String hostRockId : definition.hostRockIds()) {
            Block hostBlock = hostRockBlocks.get(hostRockId);

            if (hostBlock != null && state.is(hostBlock)) {
                return true;
            }
        }

        return false;
    }

    private static boolean passesDensity(OreVeinInstance vein, int x, int y, int z) {
        double edge = vein.edgeFactor(x, y, z);
        float density = vein.definition().settings().density();
        double noisyDensity = density * Math.clamp(edge * 1.35, 0.0, 1.0);
        int roll = Math.floorMod(stableBlockHash(vein.seed(), x, y, z), 10000);

        return roll < noisyDensity * 10000.0;
    }

    private static OreVeinOreEntry selectOreEntry(OreVeinInstance vein, int x, int y, int z) {
        List<OreVeinOreEntry> entries = vein.definition().entries();
        VeinShapeTypes type = vein.definition().shape().type();

        return switch (type) {
            case LAYERED, DISC -> selectLayeredEntry(entries, vein.layerT(x, y, z));
            case CLASSIC_BANDED -> selectClassicEntry(entries, vein.edgeFactor(x, y, z), vein.layerT(x, y, z), vein.seed(), x, y, z);
            case CUBOID -> selectCuboidEntry(entries, vein.layerT(x, y, z), vein.seed(), x, y, z);
            case DIKE -> selectWeightedEntry(entries, OreVeinOreRole.DIKE, vein.seed(), x, y, z);
            case MIXED_CLUSTER, PIPE, GEODE -> selectWeightedAny(entries, vein.seed(), x, y, z);
        };
    }

    private static OreVeinOreEntry selectLayeredEntry(List<OreVeinOreEntry> entries, double layerT) {
        List<OreVeinOreEntry> layers = entries.stream()
                .filter(entry -> entry.role() == OreVeinOreRole.LAYER)
                .toList();

        if (layers.isEmpty()) {
            return entries.getFirst();
        }

        int index = Math.min(layers.size() - 1, Math.max(0, (int) Math.floor(layerT * layers.size())));
        return layers.get(index);
    }

    private static OreVeinOreEntry selectClassicEntry(List<OreVeinOreEntry> entries, double edge, double layerT, long seed, int x, int y, int z) {
        if (edge < 0.22) {
            OreVeinOreEntry sporadic = firstRole(entries, OreVeinOreRole.SPORADIC);
            if (sporadic != null && Math.floorMod(stableBlockHash(seed ^ 0xD1B54A32D192ED03L, x, y, z), 5) == 0) {
                return sporadic;
            }
        }

        OreVeinOreEntry between = firstRole(entries, OreVeinOreRole.BETWEEN);
        if (between != null && layerT > 0.42 && layerT < 0.58) {
            return between;
        }

        OreVeinOreEntry selected = layerT < 0.5
                ? firstRole(entries, OreVeinOreRole.PRIMARY)
                : firstRole(entries, OreVeinOreRole.SECONDARY);

        return selected != null ? selected : selectWeightedAny(entries, seed, x, y, z);
    }

    private static OreVeinOreEntry selectCuboidEntry(List<OreVeinOreEntry> entries, double layerT, long seed, int x, int y, int z) {
        OreVeinOreEntry spread = firstRole(entries, OreVeinOreRole.SPREAD);
        if (spread != null && Math.floorMod(stableBlockHash(seed ^ 0xA4093822299F31D0L, x, y, z), 7) == 0) {
            return spread;
        }

        OreVeinOreEntry selected;
        if (layerT < 0.33) {
            selected = firstRole(entries, OreVeinOreRole.BOTTOM);
        } else if (layerT < 0.66) {
            selected = firstRole(entries, OreVeinOreRole.MIDDLE);
        } else {
            selected = firstRole(entries, OreVeinOreRole.TOP);
        }

        return selected != null ? selected : selectWeightedAny(entries, seed, x, y, z);
    }

    private static OreVeinOreEntry firstRole(List<OreVeinOreEntry> entries, OreVeinOreRole role) {
        return entries.stream()
                .filter(entry -> entry.role() == role)
                .findFirst()
                .orElse(null);
    }

    private static OreVeinOreEntry selectWeightedEntry(List<OreVeinOreEntry> entries, OreVeinOreRole role, long seed, int x, int y, int z) {
        List<OreVeinOreEntry> filtered = entries.stream()
                .filter(entry -> entry.role() == role)
                .toList();

        if (filtered.isEmpty()) {
            return selectWeightedAny(entries, seed, x, y, z);
        }

        return selectWeightedAny(filtered, seed, x, y, z);
    }

    private static OreVeinOreEntry selectWeightedAny(List<OreVeinOreEntry> entries, long seed, int x, int y, int z) {
        int totalWeight = entries.stream()
                .mapToInt(entry -> Math.max(1, entry.weight()))
                .sum();
        int roll = Math.floorMod(stableBlockHash(seed ^ 0x082EFA98EC4E6C89L, x, y, z), totalWeight);

        for (OreVeinOreEntry entry : entries) {
            roll -= Math.max(1, entry.weight());

            if (roll < 0) {
                return entry;
            }
        }

        return entries.getLast();
    }

    private static BlockState selectResourceState(OreVeinInstance vein, String oreId, int x, int y, int z) {
        ResourceBlockStates states = vein.resourceStates();
        BlockState normalState = states.get(oreId, OreBlockForm.NORMAL);

        if (normalState == null) {
            return null;
        }

        BlockState rawBlockState = states.get(oreId, OreBlockForm.RAW_BLOCK);
        if (rawBlockState != null && rollChance(vein.seed() ^ RAW_BLOCK_SALT, x, y, z, RAW_BLOCK_CHANCE)) {
            return rawBlockState;
        }

        double edge = vein.edgeFactor(x, y, z);

        if (edge >= RICH_ORE_EDGE_BEGIN) {
            BlockState richState = states.get(oreId, OreBlockForm.RICH);
            double richChance = ((edge - RICH_ORE_EDGE_BEGIN) / (1.0 - RICH_ORE_EDGE_BEGIN)) * MAX_RICH_ORE_CHANCE;

            if (richState != null && rollChance(vein.seed() ^ RICH_ORE_SALT, x, y, z, richChance)) {
                return richState;
            }
        } else if (edge <= POOR_ORE_EDGE_END) {
            BlockState poorState = states.get(oreId, OreBlockForm.POOR);
            double poorChance = ((POOR_ORE_EDGE_END - edge) / POOR_ORE_EDGE_END) * MAX_POOR_ORE_CHANCE;

            if (poorState != null && rollChance(vein.seed() ^ POOR_ORE_SALT, x, y, z, poorChance)) {
                return poorState;
            }
        }

        return normalState;
    }

    private static boolean rollChance(long seed, int x, int y, int z, double chance) {
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 1.0) {
            return true;
        }

        int roll = Math.floorMod(stableBlockHash(seed, x, y, z), 10000);
        return roll < chance * 10000.0;
    }

    private static ResourceBlockStates findResourceStates() {
        Map<OreBlockForm, Map<String, BlockState>> oreStates = new LinkedHashMap<>();
        for (OreBlockForm form : OreBlockForm.values()) {
            oreStates.put(form, new LinkedHashMap<>());
        }

        for (Map.Entry<MineralResource, ?> ignored : GTBlocks.ORE_BLOCKS.entrySet()) {
            // The wildcard loop below keeps generic type noise away from the
            // public registry map while still exposing a simple id -> state map.
        }

        GTBlocks.ORE_FORM_BLOCKS.forEach((form, blocks) ->
                blocks.forEach((ore, block) -> oreStates.get(form).put(ore.id(), block.get().defaultBlockState()))
        );
        GTBlocks.SAND_BLOCKS.forEach((sand, block) -> oreStates.get(OreBlockForm.NORMAL).put(sand.id(), block.get().defaultBlockState()));
        return new ResourceBlockStates(oreStates);
    }

    private static Map<String, Block> findHostRockBlocks() {
        Map<String, Block> blocks = new LinkedHashMap<>();

        GTBlocks.ROCK_BLOCKS.forEach((rock, block) -> blocks.put(rock.id(), block.get()));
        return Map.copyOf(blocks);
    }

    private static int randomBetween(long seed, int chunkX, int chunkZ, int minInclusive, int maxInclusive) {
        return minInclusive + Math.floorMod(stableChunkHash(seed, chunkX, chunkZ), maxInclusive - minInclusive + 1);
    }

    private static int stableChunkHash(long seed, int chunkX, int chunkZ) {
        long value = seed;
        value ^= chunkX * 0x9E3779B97F4A7C15L;
        value ^= chunkZ * 0xC2B2AE3D27D4EB4FL;
        return avalanche(value);
    }

    private static int stableBlockHash(long seed, int x, int y, int z) {
        long value = seed;
        value ^= x * 0x9E3779B97F4A7C15L;
        value ^= y * 0xD1B54A32D192ED03L;
        value ^= z * 0xC2B2AE3D27D4EB4FL;
        return avalanche(value);
    }

    private static int avalanche(long value) {
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;

        return (int) value;
    }

    private record OreVeinInstance(
            OreVeinDefinition definition,
            ResourceBlockStates resourceStates,
            String targetHostRockId,
            int originChunkX,
            int originChunkZ,
            int centerX,
            int centerY,
            int centerZ,
            int radiusX,
            int radiusY,
            int radiusZ,
            double angle,
            double tiltX,
            double tiltZ
    ) {
        private long seed() {
            return SEED_SALT
                    ^ definition.id().hashCode()
                    ^ (long) centerX * 0x9E3779B97F4A7C15L
                    ^ (long) centerY * 0xD1B54A32D192ED03L
                    ^ (long) centerZ * 0xC2B2AE3D27D4EB4FL;
        }

        private boolean contains(int x, int y, int z) {
            VeinShapeTypes type = definition.shape().type();

            return switch (type) {
                case DIKE -> containsDike(x, y, z);
                case CUBOID -> containsCuboid(x, y, z);
                case PIPE -> containsPipe(x, y, z);
                default -> normalizedDistance(x, y, z) <= 1.0;
            };
        }

        private double edgeFactor(int x, int y, int z) {
            VeinShapeTypes type = definition.shape().type();
            double distance = switch (type) {
                case DIKE -> dikeDistance(x, y, z);
                case CUBOID -> cuboidDistance(x, y, z);
                case PIPE -> pipeDistance(x, y, z);
                default -> normalizedDistance(x, y, z);
            };

            return Math.clamp(1.0 - distance, 0.0, 1.0);
        }

        private double layerT(int x, int y, int z) {
            double shiftedY = y - centerY + ((x - centerX) * tiltX) + ((z - centerZ) * tiltZ);
            return Math.clamp((shiftedY + radiusY) / (radiusY * 2.0), 0.0, 0.999999);
        }

        private double normalizedDistance(int x, int y, int z) {
            Local local = local(x, y, z);
            double nx = local.x() / radiusX;
            double ny = local.y() / radiusY;
            double nz = local.z() / radiusZ;

            return (nx * nx) + (ny * ny) + (nz * nz);
        }

        private boolean containsDike(int x, int y, int z) {
            return dikeDistance(x, y, z) <= 1.0;
        }

        private double dikeDistance(int x, int y, int z) {
            Local local = local(x, y, z);
            double along = Math.abs(local.x()) / Math.max(1.0, radiusX);
            double thickness = Math.abs(local.z()) / Math.max(2.0, radiusZ * 0.22);
            double height = Math.abs(local.y()) / Math.max(1.0, radiusY);

            return Math.max(along, Math.max(thickness, height));
        }

        private boolean containsCuboid(int x, int y, int z) {
            return cuboidDistance(x, y, z) <= 1.0;
        }

        private double cuboidDistance(int x, int y, int z) {
            Local local = local(x, y, z);
            return Math.max(
                    Math.abs(local.x()) / Math.max(1.0, radiusX),
                    Math.max(
                            Math.abs(local.y()) / Math.max(1.0, radiusY),
                            Math.abs(local.z()) / Math.max(1.0, radiusZ)
                    )
            );
        }

        private boolean containsPipe(int x, int y, int z) {
            return pipeDistance(x, y, z) <= 1.0;
        }

        private double pipeDistance(int x, int y, int z) {
            Local local = local(x, y, z);
            double nx = local.x() / Math.max(2.0, radiusX * 0.35);
            double nz = local.z() / Math.max(2.0, radiusZ * 0.35);

            return (nx * nx) + (nz * nz);
        }

        private Local local(int x, int y, int z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double localX = dx * cos - dz * sin;
            double localZ = dx * sin + dz * cos;
            double localY = y - centerY + (dx * tiltX) + (dz * tiltZ);

            return new Local(localX, localY, localZ);
        }

        private boolean intersectsChunk(int minX, int minZ, int minY, int maxY) {
            int maxX = minX + 15;
            int maxZ = minZ + 15;

            return maxX >= minX() && minX <= maxX()
                    && maxZ >= minZ() && minZ <= maxZ()
                    && maxY >= minY() && minY <= maxY();
        }

        private int minX() {
            return centerX - radiusX - radiusZ - 2;
        }

        private int maxX() {
            return centerX + radiusX + radiusZ + 2;
        }

        private int minY() {
            return centerY - radiusY - 2;
        }

        private int maxY() {
            return centerY + radiusY + 2;
        }

        private int minZ() {
            return centerZ - radiusX - radiusZ - 2;
        }

        private int maxZ() {
            return centerZ + radiusX + radiusZ + 2;
        }
    }

    private record Local(double x, double y, double z) {
    }

    private record VerticalHostRun(String rockId, int minY, int maxY) {
        private static VerticalHostRun of(String rockId, int minY, int maxY) {
            int minCenterY = minY + CENTER_VERTICAL_HOST_MARGIN_BLOCKS;
            int maxCenterY = maxY - CENTER_VERTICAL_HOST_MARGIN_BLOCKS;

            if (minCenterY > maxCenterY) {
                return null;
            }

            return new VerticalHostRun(rockId, minY, maxY);
        }

        private int length() {
            return maxY - minY + 1;
        }

        private int minCenterY() {
            return minY + CENTER_VERTICAL_HOST_MARGIN_BLOCKS;
        }

        private int maxCenterY() {
            return maxY - CENTER_VERTICAL_HOST_MARGIN_BLOCKS;
        }
    }

    private record ResourceBlockStates(Map<OreBlockForm, Map<String, BlockState>> oreStates) {
        private ResourceBlockStates {
            Map<OreBlockForm, Map<String, BlockState>> copied = new LinkedHashMap<>();
            oreStates.forEach((form, states) -> copied.put(form, Map.copyOf(states)));
            oreStates = Map.copyOf(copied);
        }

        private boolean isEmpty() {
            return oreStates.values().stream().allMatch(Map::isEmpty);
        }

        private BlockState get(String oreId, OreBlockForm form) {
            Map<String, BlockState> states = oreStates.get(form);

            if (states == null) {
                return null;
            }

            return states.get(oreId);
        }
    }
}
