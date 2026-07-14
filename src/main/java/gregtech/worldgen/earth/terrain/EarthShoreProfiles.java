/*
 * Shore weighting and height formulas are adapted from TerraFirmaGreg's
 * EUPL-1.2 ShoreNoise pipeline. TerraFirmaGreg is credited in NOTICE.md.
 */
package gregtech.worldgen.earth.terrain;

import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Weighted coast-height modifiers for the climate-neutral Earth profiles. */
public final class EarthShoreProfiles {
    private static final int SEA_LEVEL = EarthTerrainProfiles.SEA_LEVEL;

    private final FractalNoise tide;
    private final FractalNoise dunes;
    private final FractalNoise shelfIntensity;
    private final FractalNoise shelfSteepness;
    private final FractalNoise shelfRoughness;
    private final FractalNoise tidePools;

    public EarthShoreProfiles(long seed) {
        XoroshiroRandomSource sequence = new XoroshiroRandomSource(seed);
        tide = new FractalNoise(sequence.nextLong(), 3, 0.005);
        dunes = new FractalNoise(sequence.nextLong(), 3, 0.03);
        shelfIntensity = new FractalNoise(sequence.nextLong(), 3, 0.03);
        shelfSteepness = new FractalNoise(sequence.nextLong(), 2, 0.11);
        shelfRoughness = new FractalNoise(sequence.nextLong(), 3, 0.09);
        tidePools = new FractalNoise(sequence.nextLong(), 3, 0.09);
    }

    public double adjustHeight(
            double height,
            int blockX,
            int blockZ,
            double[] profileWeights
    ) {
        TerrainProfile[] profiles = TerrainProfile.values();
        double oceanWeight = 0.0;
        double shoreWeight = 0.0;
        for (int index = 0; index < profiles.length; index++) {
            TerrainProfile profile = profiles[index];
            if (isOcean(profile)) oceanWeight += profileWeights[index];
            if (isShore(profile)) shoreWeight += profileWeights[index];
        }
        if (shoreWeight <= 0.0) return height;
        double landWeight = Math.max(0.0, 1.0 - oceanWeight - shoreWeight);
        double output = height * Math.max(0.0, 1.0 - shoreWeight);

        for (int index = 0; index < profiles.length; index++) {
            TerrainProfile profile = profiles[index];
            double weight = profileWeights[index];
            if (weight <= 0.0 || !isShore(profile)) continue;
            double candidate = switch (profile) {
                case SHORE, TIDAL_FLATS -> sandyHeight(
                        height, blockX, blockZ, landWeight, oceanWeight
                );
                case COASTAL_DUNES -> duneHeight(
                        height, blockX, blockZ, landWeight, oceanWeight, weight
                );
                case EMBAYMENTS -> shelfHeight(
                        height, blockX, blockZ, landWeight, oceanWeight, true
                );
                case ROCKY_SHORES -> shelfHeight(
                        height, blockX, blockZ, landWeight, oceanWeight, false
                );
                default -> height;
            };
            output += weight * candidate;
        }
        return output;
    }

    public static boolean isShore(TerrainProfile profile) {
        return profile == TerrainProfile.SHORE
                || profile == TerrainProfile.TIDAL_FLATS
                || profile == TerrainProfile.COASTAL_DUNES
                || profile == TerrainProfile.EMBAYMENTS
                || profile == TerrainProfile.ROCKY_SHORES;
    }

    private double sandyHeight(
            double height,
            int x,
            int z,
            double landWeight,
            double oceanWeight
    ) {
        return simpleBeach(tideLevel(x, z), height, landWeight, oceanWeight);
    }

    private double duneHeight(
            double height,
            int x,
            int z,
            double landWeight,
            double oceanWeight,
            double thisWeight
    ) {
        double sandHeight = simpleBeach(tideLevel(x, z), height, landWeight, oceanWeight);
        double tideAtOcean = tideLevel(x, z) - 4.0;
        double fullDune = sandHeight + map(Math.abs(dunes.sample(x, z)), 0.0, 1.0, -2.0, 6.5);
        double duneHeight = oceanWeight > 0.0
                ? clampedMap(oceanWeight, 0.0, 0.25, fullDune, tideAtOcean)
                : fullDune;
        return clampedMap(thisWeight, 0.5, 1.0, sandHeight, duneHeight);
    }

    private double shelfHeight(
            double heightIn,
            int x,
            int z,
            double landWeight,
            double oceanWeight,
            boolean sandyEmbayment
    ) {
        double tideLevel = tideLevel(x, z);
        double sandHeight = simpleBeach(tideLevel, heightIn, landWeight, oceanWeight);
        double roughness = shelfRoughness.sample(x, z) * 3.0
                + clampedMap(tidePools.sample(x, z), -1.0, 0.0, -5.0, 0.0);
        double topHeight = SEA_LEVEL + roughness + 15.0;
        double middleHeight = SEA_LEVEL + roughness + 9.0;
        double lowHeight = SEA_LEVEL + roughness + 3.0;

        double baseProgress = map(shelfIntensity.sample(x, z), -1.0, 1.0, -0.8, 1.0);
        double progress;
        if (oceanWeight > 0.10) {
            progress = clampedMap(
                    oceanWeight, 0.1, 0.3,
                    baseProgress, baseProgress - oceanWeight * 3.0
            );
        } else {
            double landInfluence = clampedMap(
                    landWeight, 0.4, 0.0,
                    baseProgress + landWeight * 2.0, baseProgress
            );
            progress = clampedMap(
                    oceanWeight, 0.0, 0.1,
                    baseProgress + landInfluence, baseProgress
            );
        }
        double steepness = clampedMap(shelfSteepness.sample(x, z), -0.7, 0.7, 0.0, 0.3);
        if (progress > 0.7 + steepness) return Math.min(heightIn, topHeight);
        if (progress > 0.7) {
            return Math.min(heightIn, map(progress, 0.7, 0.7 + steepness, middleHeight, topHeight));
        }
        if (progress > 0.35 + steepness) return Math.min(heightIn, middleHeight);
        if (progress > 0.35) {
            double lower = sandyEmbayment ? sandHeight : lowHeight;
            return Math.min(heightIn, map(progress, 0.35, 0.35 + steepness, lower, middleHeight));
        }
        if (sandyEmbayment) return Math.min(heightIn, sandHeight);
        if (progress > 0.10 - steepness) return Math.min(heightIn, lowHeight);
        return Math.min(heightIn, tideLevel - 4.0);
    }

    private double tideLevel(int x, int z) {
        double broad = map(tide.sample(x, z), -1.0, 1.0, SEA_LEVEL - 6.0, SEA_LEVEL + 6.0);
        broad = Math.clamp(broad, SEA_LEVEL, SEA_LEVEL + 4.0);
        return broad + tide.sample(x + 10_003, z - 7_919);
    }

    /** TFG's post-shore ocean-side ceiling: local tide height minus four. */
    double oceanEdgeHeight(int x, int z) {
        return tideLevel(x, z) - 4.0;
    }

    private static double simpleBeach(
            double tideLevel,
            double height,
            double landWeight,
            double oceanWeight
    ) {
        double landHeight = tideLevel + 2.0;
        double selfHeight = tideLevel - 2.0;
        double oceanHeight = tideLevel - 4.0;
        if (oceanWeight > 0.10) {
            return clampedMap(oceanWeight, 0.1, 0.25, selfHeight, oceanHeight);
        }
        double landDerived = landWeight < 0.3
                ? map(landWeight, 0.3, 0.0, Math.min(height, landHeight), selfHeight)
                : clampedMap(landWeight, 0.3, 0.5, Math.min(height, landHeight), height);
        return clampedMap(oceanWeight, 0.0, 0.1, landDerived, selfHeight);
    }

    private static boolean isOcean(TerrainProfile profile) {
        return profile == TerrainProfile.DEEP_OCEAN || profile == TerrainProfile.OCEAN;
    }

    private static double clampedMap(
            double value,
            double inMin,
            double inMax,
            double outMin,
            double outMax
    ) {
        if (inMin == inMax) return outMin;
        return map(Math.clamp(value, Math.min(inMin, inMax), Math.max(inMin, inMax)),
                inMin, inMax, outMin, outMax);
    }

    private static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
    }

    private static final class FractalNoise {
        private final SimplexNoise[] octaves;
        private final double initialFrequency;
        private final double amplitudeSum;

        private FractalNoise(long seed, int octaveCount, double spread) {
            XoroshiroRandomSource random = new XoroshiroRandomSource(seed);
            octaves = new SimplexNoise[octaveCount];
            double amplitude = 1.0;
            double sum = 0.0;
            for (int index = 0; index < octaveCount; index++) {
                octaves[index] = new SimplexNoise(random);
                sum += amplitude;
                amplitude *= 0.5;
            }
            initialFrequency = spread / (1 << Math.max(0, octaveCount - 1));
            amplitudeSum = sum;
        }

        private double sample(double x, double z) {
            double frequency = initialFrequency;
            double amplitude = 1.0;
            double value = 0.0;
            for (SimplexNoise octave : octaves) {
                value += octave.getValue(x * frequency, z * frequency) * amplitude;
                frequency *= 2.0;
                amplitude *= 0.5;
            }
            return Math.clamp(value / amplitudeSum, -1.0, 1.0);
        }
    }
}
