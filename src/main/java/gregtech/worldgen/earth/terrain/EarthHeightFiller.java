/*
 * Weighted sampling structure follows TerraFirmaCraft's ChunkBiomeSampler and
 * ChunkHeightFiller. TerraFirmaCraft is credited in NOTICE.md.
 */
package gregtech.worldgen.earth.terrain;

import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import gregtech.worldgen.earth.river.EarthRiverSampler;
import gregtech.worldgen.earth.river.EarthRiverSampler.RiverInfo;
import net.minecraft.world.level.ChunkPos;

import java.util.Arrays;

/**
 * Earth surface-height sampler shared by runtime queries and PNG previews.
 *
 * <p>This is a lazy equivalent of TFC's 7x7 quart-profile array. For a single
 * column it computes only the four 7x7 entries which can contribute to that
 * column, while retaining the same 9x9 kernels, wide land/ocean composition
 * and bilinear interpolation.</p>
 */
public final class EarthHeightFiller {
    private static final TerrainProfile[] PROFILES = TerrainProfile.values();
    private static final int PROFILE_COUNT = PROFILES.length;
    private static final int KERNEL_RADIUS = 4;
    private static final double KERNEL_FACTOR = 0.0211640211641D;
    private static final int CACHE_SIZE = 1 << 14;

    private final EarthTerrainProfiles profiles;
    private final EarthShoreProfiles shores;
    private final EarthRiverSampler rivers;
    private final EarthKarstModel karst;
    private final ThreadLocal<SamplingCaches> caches = ThreadLocal.withInitial(SamplingCaches::new);

    public EarthHeightFiller(
            EarthTerrainProfiles profiles,
            EarthShoreProfiles shores,
            EarthRiverSampler rivers,
            EarthKarstModel karst
    ) {
        this.profiles = profiles;
        this.shores = shores;
        this.rivers = rivers;
        this.karst = karst;
    }

    public double sampleHeight(int blockX, int blockZ) {
        return sampleColumn(blockX, blockZ).height();
    }

    /** Samples final height and river membership from one shared weight field. */
    public ColumnSample sampleColumn(int blockX, int blockZ) {
        SamplingCaches local = caches.get();
        Arrays.fill(local.columnWeights, 0.0);

        int quartBlockX = Math.floorDiv(blockX, 4) * 4;
        int quartBlockZ = Math.floorDiv(blockZ, 4) * 4;
        double lerpX = Math.floorMod(blockX, 4) * 0.25;
        double lerpZ = Math.floorMod(blockZ, 4) * 0.25;

        addComposedQuart(local, quartBlockX, quartBlockZ,
                (1.0 - lerpX) * (1.0 - lerpZ), local.columnWeights);
        addComposedQuart(local, quartBlockX + 4, quartBlockZ,
                lerpX * (1.0 - lerpZ), local.columnWeights);
        addComposedQuart(local, quartBlockX, quartBlockZ + 4,
                (1.0 - lerpX) * lerpZ, local.columnWeights);
        addComposedQuart(local, quartBlockX + 4, quartBlockZ + 4,
                lerpX * lerpZ, local.columnWeights);

        double height = 0.0;
        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            height += local.columnWeights[profile] * profiles.height(PROFILES[profile], blockX, blockZ);
        }
        height = shores.adjustHeight(height, blockX, blockZ, local.columnWeights);

        /*
         * TFG performs this second coast guard after all shore contributions.
         * Without it, the wide land kernel can keep an ocean-dominant column
         * above sea level and produce detached shelves / accidental islets on
         * the ocean side of a coast.
         */
        double oceanWeight = 0.0;
        double shoreWeight = 0.0;
        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            TerrainProfile terrain = PROFILES[profile];
            if (terrain == TerrainProfile.DEEP_OCEAN || terrain == TerrainProfile.OCEAN) {
                oceanWeight += local.columnWeights[profile];
            } else if (EarthShoreProfiles.isShore(terrain)) {
                shoreWeight += local.columnWeights[profile];
            }
        }
        double landWeight = Math.max(0.0, 1.0 - oceanWeight - shoreWeight);
        if (oceanWeight >= 0.25) {
            double oceanEdgeHeight = shores.oceanEdgeHeight(blockX, blockZ);
            height = clampedMap(
                    landWeight,
                    0.32,
                    0.36,
                    Math.min(height, oceanEdgeHeight),
                    height
            );
        }

        RiverInfo river = rivers.sample(blockX, blockZ);
        height = rivers.adjustHeight(height, blockX, blockZ, river, local.columnWeights);
        height = karst.adjustHeight(height, blockX, blockZ);

        /*
         * A final semantic ocean invariant prevents the wide coast kernel from
         * lifting isolated ocean pixels to Y=61..63. Shores still own their
         * gradual shelves, while an actual OCEAN/DEEP_OCEAN cell always has a
         * useful water column instead of a one-block puddle or sand islet.
         */
        TerrainProfile centerProfile = profiles.profileAtBlock(blockX, blockZ);
        if (centerProfile == TerrainProfile.OCEAN || centerProfile == TerrainProfile.DEEP_OCEAN) {
            double openOcean = Math.clamp((oceanWeight - 0.35) / 0.65, 0.0, 1.0);
            double minimumDepth = centerProfile == TerrainProfile.DEEP_OCEAN
                    ? 12.0 + 6.0 * openOcean
                    : 5.0 + 5.0 * openOcean;
            height = Math.min(height, EarthTerrainProfiles.SEA_LEVEL - minimumDepth);
        }

        boolean riverCore = river != null && river.normalizedDistanceSq() <= 1.0;
        return new ColumnSample(height, riverCore);
    }

    public int sampleBlockHeight(int blockX, int blockZ) {
        return (int) Math.floor(sampleHeight(blockX, blockZ));
    }

    public record ColumnSample(double height, boolean riverCore) {
    }

    private static double clampedMap(
            double value,
            double inMin,
            double inMax,
            double outMin,
            double outMax
    ) {
        double delta = Math.clamp((value - inMin) / (inMax - inMin), 0.0, 1.0);
        return outMin + delta * (outMax - outMin);
    }

    private void addComposedQuart(
            SamplingCaches local,
            int blockX,
            int blockZ,
            double contribution,
            double[] accumulator
    ) {
        if (contribution <= 0.0) return;
        int slot = local.quartWeights.find(blockX, blockZ);
        if (!local.quartWeights.matches(slot, blockX, blockZ)) {
            computeComposedQuart(local, blockX, blockZ, local.quartWeights.values, slot * PROFILE_COUNT);
            local.quartWeights.storeKey(slot, blockX, blockZ);
        }
        int offset = slot * PROFILE_COUNT;
        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            accumulator[profile] += local.quartWeights.values[offset + profile] * contribution;
        }
    }

    private void computeComposedQuart(
            SamplingCaches local,
            int blockX,
            int blockZ,
            double[] output,
            int outputOffset
    ) {
        Arrays.fill(local.fineWeights, 0.0);
        Arrays.fill(local.wideWeights, 0.0);
        sampleKernel(blockX, blockZ, 4, local.fineWeights);

        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        double lerpX = Math.floorMod(blockX, 16) / 16.0;
        double lerpZ = Math.floorMod(blockZ, 16) / 16.0;
        addChunkKernel(local, chunkX, chunkZ, (1.0 - lerpX) * (1.0 - lerpZ), local.wideWeights);
        addChunkKernel(local, chunkX + 1, chunkZ, lerpX * (1.0 - lerpZ), local.wideWeights);
        addChunkKernel(local, chunkX, chunkZ + 1, (1.0 - lerpX) * lerpZ, local.wideWeights);
        addChunkKernel(local, chunkX + 1, chunkZ + 1, lerpX * lerpZ, local.wideWeights);

        double fineLand = 0.0;
        double fineOcean = 0.0;
        double wideLand = 0.0;
        double wideOcean = 0.0;
        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            if (isOceanBlend(PROFILES[profile])) {
                fineOcean += local.fineWeights[profile];
                wideOcean += local.wideWeights[profile];
            } else {
                fineLand += local.fineWeights[profile];
                wideLand += local.wideWeights[profile];
            }
        }

        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            boolean ocean = isOceanBlend(PROFILES[profile]);
            double actualGroup = ocean ? fineOcean : fineLand;
            double wideGroup = ocean ? wideOcean : wideLand;
            output[outputOffset + profile] = actualGroup > 0.0 && wideGroup > 0.0
                    ? local.wideWeights[profile] * actualGroup / wideGroup
                    : 0.0;
        }
    }

    private void addChunkKernel(
            SamplingCaches local,
            int chunkX,
            int chunkZ,
            double contribution,
            double[] accumulator
    ) {
        if (contribution <= 0.0) return;
        int slot = local.chunkWeights.find(chunkX, chunkZ);
        if (!local.chunkWeights.matches(slot, chunkX, chunkZ)) {
            int offset = slot * PROFILE_COUNT;
            Arrays.fill(local.chunkWeights.values, offset, offset + PROFILE_COUNT, 0.0);
            sampleKernel(chunkX << 4, chunkZ << 4, 16, local.chunkWeights.values, offset);
            local.chunkWeights.storeKey(slot, chunkX, chunkZ);
        }
        int offset = slot * PROFILE_COUNT;
        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            accumulator[profile] += local.chunkWeights.values[offset + profile] * contribution;
        }
    }

    private void sampleKernel(int blockX, int blockZ, int step, double[] output) {
        sampleKernel(blockX, blockZ, step, output, 0);
    }

    private void sampleKernel(int blockX, int blockZ, int step, double[] output, int offset) {
        for (int dz = -KERNEL_RADIUS; dz <= KERNEL_RADIUS; dz++) {
            for (int dx = -KERNEL_RADIUS; dx <= KERNEL_RADIUS; dx++) {
                double weight = KERNEL_FACTOR * (1.0 - 0.03125 * (dx * dx + dz * dz));
                TerrainProfile profile = profiles.profileAtBlock(blockX + dx * step, blockZ + dz * step);
                output[offset + profile.ordinal()] += weight;
            }
        }
    }

    private static boolean isOceanBlend(TerrainProfile profile) {
        return profile == TerrainProfile.DEEP_OCEAN
                || profile == TerrainProfile.OCEAN
                || profile == TerrainProfile.TIDAL_FLATS;
    }

    private static final class SamplingCaches {
        private final WeightCache quartWeights = new WeightCache(CACHE_SIZE);
        private final WeightCache chunkWeights = new WeightCache(CACHE_SIZE >> 1);
        private final double[] fineWeights = new double[PROFILE_COUNT];
        private final double[] wideWeights = new double[PROFILE_COUNT];
        private final double[] columnWeights = new double[PROFILE_COUNT];
    }

    private static final class WeightCache {
        private final long[] keys;
        private final boolean[] valid;
        private final double[] values;
        private final int mask;

        private WeightCache(int size) {
            keys = new long[size];
            valid = new boolean[size];
            values = new double[size * PROFILE_COUNT];
            mask = size - 1;
        }

        private int find(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            long mixed = key;
            mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
            mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
            mixed ^= mixed >>> 31;
            return (int) (mixed ^ (mixed >>> 32)) & mask;
        }

        private boolean matches(int slot, int x, int z) {
            return valid[slot] && keys[slot] == ChunkPos.asLong(x, z);
        }

        private void storeKey(int slot, int x, int z) {
            keys[slot] = ChunkPos.asLong(x, z);
            valid[slot] = true;
        }
    }
}
