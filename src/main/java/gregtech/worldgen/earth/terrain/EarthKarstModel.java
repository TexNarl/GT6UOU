package gregtech.worldgen.earth.terrain;

import gregtech.worldgen.earth.climate.EarthClimateModel;
import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import gregtech.worldgen.geology.StoneLayerStackSampler;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * TFG climate gates for karst terrain, adapted to GT6UOU's real surface rock.
 * TFG chooses separate tower/shilin/doline/cenote biome noises; we preserve
 * those semantic gates while applying their relief as a final height modifier
 * because this port deliberately reuses vanilla biome holders.
 */
public final class EarthKarstModel {
    private final StoneLayerStackSampler rocks;
    private final EarthClimateModel climate;
    private final EarthTerrainProfiles terrainProfiles;
    private final SimplexNoise broad;
    private final SimplexNoise detail;
    private final ThreadLocal<KarstCache> cache = ThreadLocal.withInitial(KarstCache::new);

    public EarthKarstModel(
            long seed,
            StoneLayerStackSampler rocks,
            EarthClimateModel climate,
            EarthTerrainProfiles terrainProfiles
    ) {
        this.rocks = rocks;
        this.climate = climate;
        this.terrainProfiles = terrainProfiles;
        XoroshiroRandomSource random = new XoroshiroRandomSource(seed ^ 0xDB4F0B9175AE2165L);
        broad = new SimplexNoise(random);
        detail = new SimplexNoise(random);
    }

    public boolean isKarst(int blockX, int blockZ) {
        TerrainProfile terrain = terrainProfiles.profileAtBlock(blockX, blockZ);
        if (terrain == TerrainProfile.OCEAN || terrain == TerrainProfile.DEEP_OCEAN) {
            return false;
        }
        KarstCache local = cache.get();
        int slot = local.slot(blockX, blockZ);
        long key = ((long) blockX << 32) ^ (blockZ & 0xFFFFFFFFL);
        if (local.valid[slot] && local.keys[slot] == key) return local.values[slot];
        String rock = rocks.sampleRockId(blockX, 63, blockZ, 63);
        boolean value = rock.equals("limestone") || rock.equals("dolomite")
                || rock.equals("chalk") || rock.equals("marble");
        local.keys[slot] = key;
        local.values[slot] = value;
        local.valid[slot] = true;
        return value;
    }

    public double adjustHeight(double height, int blockX, int blockZ) {
        if (!isKarst(blockX, blockZ)) return height;
        var sample = climate.sample(blockX, blockZ);
        float rain = sample.averageRainfall();
        float temperature = sample.averageTemperature();
        if (rain <= 250.0F) return height;

        double large = broad.getValue(blockX * 0.0065, blockZ * 0.0065);
        double small = detail.getValue(blockX * 0.027, blockZ * 0.027);
        if (rain > 425.0F && rain + 10.0F * temperature > 500.0F) {
            // Tower karst: isolated steep positive towers on a karst plain.
            double tower = Math.pow(Math.max(0.0, 1.0 - Math.abs(large * 1.8)), 5.0);
            return height + tower * (25.0 + 28.0 * Math.max(0.0, small));
        }
        if (rain > 375.0F) {
            // Shilin/doline climates: rock ribs with enclosed depressions.
            double ribs = Math.pow(Math.max(0.0, 1.0 - Math.abs(small * 2.2)), 3.0) * 12.0;
            double doline = Math.pow(Math.max(0.0, large - 0.18), 2.0) * 26.0;
            return height + ribs - doline;
        }
        // Cenote climate: sparse, deeper closed sinkholes.
        double sink = Math.pow(Math.max(0.0, large - 0.35), 3.0) * 38.0;
        return height - sink;
    }

    public double caveOpeningBias(int blockX, int blockZ) {
        if (!isKarst(blockX, blockZ)) return 0.0;
        float rain = climate.averageRainfall(blockX, blockZ);
        return rain > 375.0F ? 0.18 : rain > 250.0F ? 0.10 : 0.0;
    }

    private static final class KarstCache {
        private static final int SIZE = 1 << 13;
        private final long[] keys = new long[SIZE];
        private final boolean[] values = new boolean[SIZE];
        private final boolean[] valid = new boolean[SIZE];

        private int slot(int x, int z) {
            long value = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return (int) (value ^ value >>> 31) & (SIZE - 1);
        }
    }
}
