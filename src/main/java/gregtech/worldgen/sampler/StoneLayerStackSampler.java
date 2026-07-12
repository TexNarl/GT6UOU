package gregtech.worldgen.sampler;

import gregapi.worldgen.GeologicalRules.RegionType;

import gregapi.worldgen.StoneLayerRules.StoneLayerStackDefinition;
import gregapi.data.StoneLayerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Samples TFC-style geological rock stacks for the Overworld.
 *
 * <p>The old GT6 generator picks rocks from a broad stone-layer table with 3D
 * noise. This sampler keeps the deterministic, no-neighbor-access property, but
 * chooses layers as geological stacks: surface region -> surface rock -> valid
 * lower rocks. Layer thickness and lateral skew are noise-driven so strata are
 * not perfectly horizontal.</p>
 */
public final class StoneLayerStackSampler {
    private static final int MIN_LAYER_THICKNESS = 38;
    private static final int MAX_LAYER_THICKNESS = 70;
    private static final int LOWLAND_SURFACE_Y = 62;
    private static final int MOUNTAIN_SURFACE_Y = 180;
    private static final float MIN_HEIGHT_LAYER_SCALE = 0.75F;
    private static final float MAX_HEIGHT_LAYER_SCALE = 2.25F;
    private static final float BOUNDARY_DRIFT_BLOCKS = 18.0F;
    private static final int LAYER_CENTER_DRIFT_BLOCKS = 50;
    private static final float SKEW_STRENGTH = 1.15F;
    private static final float SKEW_DEPTH_OFFSET = 12.0F;

    private final long seed;
    private final long geologicalRegionSeed;
    private final long rockChoiceSeed;
    private final long layerHeightSeed;
    private final long boundaryDriftSeed;
    private final long layerSkewXSeed;
    private final long layerSkewZSeed;

    public StoneLayerStackSampler(long seed) {
        this.seed = seed;
        this.geologicalRegionSeed = seed ^ 0x7E57_1234_AA55_9012L;
        this.rockChoiceSeed = seed ^ 0xBB67_AE85_84CA_A73BL;
        this.layerHeightSeed = seed ^ 0x43C6_12AB_9E37_79B9L;
        this.boundaryDriftSeed = seed ^ 0x3C6E_F372_FE94_F82BL;
        this.layerSkewXSeed = seed ^ 0xA409_3822_299F_31D0L;
        this.layerSkewZSeed = seed ^ 0x082E_FA98_EC4E_6C89L;
    }

    public String sampleRockId(int x, int y, int z, int surfaceY) {
        LayerDepth layerDepth = layerDepth(x, y, z, surfaceY);
        int layer = layerDepth.layer();
        float deltaYInLayer = layerDepth.deltaYInLayer();
        float heightScale = heightLayerScale(surfaceY);

        int skewedX = x + Math.round(smoothNoise2D(layerSkewXSeed + layer * 0x632B_E59BL, x, z, 220.0F) * (deltaYInLayer + SKEW_DEPTH_OFFSET) * SKEW_STRENGTH);
        int skewedZ = z + Math.round(smoothNoise2D(layerSkewZSeed + layer * 0x8515_7AF5L, x, z, 220.0F) * (deltaYInLayer + SKEW_DEPTH_OFFSET) * SKEW_STRENGTH);

        RegionType regionType = selectGeologicalRegionType(skewedX, skewedZ);
        List<String> rockStack = selectRockStack(regionType, skewedX, skewedZ);

        if (layer >= rockStack.size()) {
            return rockStack.getLast();
        }

        return rockStack.get(layer);
    }

    public StoneLayerStackSample sampleStack(int x, int z) {
        RegionType regionType = selectGeologicalRegionType(x, z);
        return new StoneLayerStackSample(regionType, selectRockStack(regionType, x, z));
    }

    private LayerDepth layerDepth(int x, int y, int z, int surfaceY) {
        float heightScale = heightLayerScale(surfaceY);
        float boundaryDrift = smoothNoise2D(boundaryDriftSeed, x, z, 900.0F) * BOUNDARY_DRIFT_BLOCKS;
        float remainingDepth = Math.max(0.0F, surfaceY - y + boundaryDrift);
        int layer = 0;

        while (true) {
            float thickness = layerThickness(x, z, layer, heightScale);
            if (remainingDepth <= thickness) {
                return new LayerDepth(layer, remainingDepth);
            }

            remainingDepth -= thickness;
            layer++;
        }
    }

    private float layerThickness(int x, int z, int layer, float heightScale) {
        int offsetX = layerOffset(layer, 0);
        int offsetZ = layerOffset(layer, 1);
        LayerCoordinates centerDrift = layerCenterDrift(layer);
        float normalized = (smoothNoise2D(
                layerHeightSeed + layer * 0x9E37_79B9L,
                x + offsetX + centerDrift.x(),
                z + offsetZ + centerDrift.z(),
                720.0F
        ) + 1.0F) * 0.5F;
        float baseThickness = MIN_LAYER_THICKNESS + normalized * (MAX_LAYER_THICKNESS - MIN_LAYER_THICKNESS);

        return baseThickness * heightScale;
    }

    private static float heightLayerScale(int surfaceY) {
        float mountainFactor = Math.clamp(
                (surfaceY - LOWLAND_SURFACE_Y) / (float) (MOUNTAIN_SURFACE_Y - LOWLAND_SURFACE_Y),
                0.0F,
                1.0F
        );

        return MIN_HEIGHT_LAYER_SCALE + mountainFactor * (MAX_HEIGHT_LAYER_SCALE - MIN_HEIGHT_LAYER_SCALE);
    }

    private RegionType selectGeologicalRegionType(int x, int z) {
        float province = smoothNoise2D(geologicalRegionSeed, x, z, 2600.0F);
        float localWarp = smoothNoise2D(geologicalRegionSeed ^ 0xA24B_AED4_963E_E407L, x, z, 850.0F);
        float value = Math.clamp(province * 0.88F + localWarp * 0.12F, -1.0F, 1.0F);

        if (value < -0.58F) {
            return RegionType.CONTINENTAL_SEDIMENTARY;
        }
        if (value < -0.33F) {
            return RegionType.UPLIFT;
        }
        if (value < -0.10F) {
            return RegionType.VOLCANIC;
        }
        if (value < 0.10F) {
            return RegionType.MAFIC_DEEP;
        }
        if (value < 0.33F) {
            return RegionType.VOLCANIC;
        }
        if (value < 0.58F) {
            return RegionType.UPLIFT;
        }

        return RegionType.CONTINENTAL_SEDIMENTARY;
    }

    private List<String> selectRockStack(RegionType type, int x, int z) {
        List<StoneLayerStackDefinition> candidates = StoneLayerList.OVERWORLD_LAYER_STACKS.stream()
                .filter(stack -> stack.type() == type)
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No Overworld stone layer stacks registered for type: " + type);
        }

        List<StoneLayerStackDefinition> weightedStacks = new ArrayList<>();

        for (StoneLayerStackDefinition stack : candidates) {
            for (int i = 0; i < stack.weight(); i++) {
                weightedStacks.add(stack);
            }
        }

        if (weightedStacks.isEmpty()) {
            throw new IllegalStateException("No weighted Overworld stone layer stacks for type: " + type);
        }

        LayerCoordinates centerDrift = layerCenterDrift(0);
        float baseX = x + centerDrift.x();
        float baseZ = z + centerDrift.z();
        LayerCoordinates coordinates = transformLayerCoordinates(0, baseX, baseZ);
        float macro = smoothNoise2D(rockChoiceSeed, coordinates.x(), coordinates.z(), 1720.0F);
        float detail = smoothNoise2D(rockChoiceSeed ^ 0x1656_67B1_9E37_79F9L, coordinates.x(), coordinates.z(), 420.0F);
        float mixed = Math.clamp(macro * 0.82F + detail * 0.18F, -1.0F, 1.0F);
        int index = Math.min(weightedStacks.size() - 1, (int) (((mixed + 1.0F) * 0.5F) * weightedStacks.size()));

        return weightedStacks.get(index).rockIds();
    }

    private LayerCoordinates transformLayerCoordinates(int layer, float x, float z) {
        int transform = Math.floorMod(stableHash(seed ^ 0xDB4F_0B91L, layer, 0, 0), 8);
        float transformedX;
        float transformedZ;

        switch (transform) {
            case 1 -> {
                transformedX = -x;
                transformedZ = z;
            }
            case 2 -> {
                transformedX = x;
                transformedZ = -z;
            }
            case 3 -> {
                transformedX = -x;
                transformedZ = -z;
            }
            case 4 -> {
                transformedX = z;
                transformedZ = x;
            }
            case 5 -> {
                transformedX = -z;
                transformedZ = x;
            }
            case 6 -> {
                transformedX = z;
                transformedZ = -x;
            }
            case 7 -> {
                transformedX = -z;
                transformedZ = -x;
            }
            default -> {
                transformedX = x;
                transformedZ = z;
            }
        }

        int offsetX = Math.floorMod(stableHash(seed ^ 0x94D0_49BBL, layer, 0, 0), 4096) - 2048;
        int offsetZ = Math.floorMod(stableHash(seed ^ 0xD1B5_4A32L, layer, 0, 0), 4096) - 2048;

        return new LayerCoordinates(transformedX + offsetX, transformedZ + offsetZ);
    }

    private LayerCoordinates layerCenterDrift(int layer) {
        if (layer == 0) {
            return new LayerCoordinates(0, 0);
        }

        int driftX = boundedLayerDrift(layer, 0);
        int driftZ = boundedLayerDrift(layer, 1);

        return new LayerCoordinates(driftX, driftZ);
    }

    private int boundedLayerDrift(int layer, int salt) {
        return Math.floorMod(stableHash(seed ^ 0x6A09_E667_F3BC_C909L, layer, salt, 0), LAYER_CENTER_DRIFT_BLOCKS * 2 + 1)
                - LAYER_CENTER_DRIFT_BLOCKS;
    }

    private static int layerOffset(int layer, int salt) {
        return stableHash(0x5DEECE66DL, layer, salt, 0) & 0xFFFF;
    }

    private static float smoothNoise2D(long seed, float x, float z, float scale) {
        float sampleX = x / scale;
        float sampleZ = z / scale;
        int x0 = (int) Math.floor(sampleX);
        int z0 = (int) Math.floor(sampleZ);
        float localX = sampleX - x0;
        float localZ = sampleZ - z0;
        float sx = smoothStep(localX);
        float sz = smoothStep(localZ);

        float v00 = randomUnit(seed, x0, z0);
        float v10 = randomUnit(seed, x0 + 1, z0);
        float v01 = randomUnit(seed, x0, z0 + 1);
        float v11 = randomUnit(seed, x0 + 1, z0 + 1);
        float a = lerp(v00, v10, sx);
        float b = lerp(v01, v11, sx);

        return lerp(a, b, sz);
    }

    private static float smoothStep(float value) {
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    private static float randomUnit(long seed, int x, int z) {
        int hash = stableHash(seed, x, 0, z);

        return ((hash >>> 8) & 0xFFFF) / 32767.5F - 1.0F;
    }

    private static int stableHash(long seed, int x, int y, int z) {
        long value = seed;
        value ^= x * 0x9E3779B97F4A7C15L;
        value ^= y * 0xC2B2AE3D27D4EB4FL;
        value ^= z * 0x165667B19E3779F9L;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;

        return (int) value;
    }

    private record LayerDepth(int layer, float deltaYInLayer) {
    }

    private record LayerCoordinates(float x, float z) {
    }

    public record StoneLayerStackSample(RegionType regionType, List<String> rockIds) {
    }
}
