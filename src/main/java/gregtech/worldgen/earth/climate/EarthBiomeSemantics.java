package gregtech.worldgen.earth.climate;

import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Climate and terrain to vanilla-biome translation for GT Earth.
 *
 * <p>TFG stores a rich custom biome id on every regional point. GT6UOU does
 * not yet own biome textures or vegetation sets, so this milestone preserves
 * the same semantic decision (ocean depth, altitude, temperature, rainfall,
 * river and karst) and deliberately maps it onto existing vanilla holders.
 * Terrain height remains owned by the Earth pipeline, never by the holder.</p>
 */
public final class EarthBiomeSemantics {
    private EarthBiomeSemantics() {
    }

    public static ResourceKey<Biome> resolve(
            TerrainProfile profile,
            float temperature,
            float rainfall,
            boolean river,
            boolean karst
    ) {
        if (isOcean(profile)) {
            return ocean(profile == TerrainProfile.DEEP_OCEAN, temperature);
        }
        if (profile == TerrainProfile.LAKE || river) {
            return temperatureAtFreezing(temperature, rainfall)
                    ? Biomes.FROZEN_RIVER
                    : Biomes.RIVER;
        }
        if (profile == TerrainProfile.VOLCANO) {
            return temperatureAtFreezing(temperature, rainfall) ? Biomes.FROZEN_PEAKS : Biomes.STONY_PEAKS;
        }

        float iceLimit = iceSheetTemperature(rainfall);
        boolean mountain = profile == TerrainProfile.MOUNTAINS
                || profile == TerrainProfile.COASTAL_MOUNTAINS;
        if (mountain) {
            if (temperature < iceLimit + 4.0F) return Biomes.FROZEN_PEAKS;
            if (rainfall < 95.0F) return Biomes.STONY_PEAKS;
            return Biomes.JAGGED_PEAKS;
        }
        if (temperature < iceLimit) {
            return profile == TerrainProfile.HIGHLANDS ? Biomes.ICE_SPIKES : Biomes.SNOWY_PLAINS;
        }
        if (temperature < -4.0F) return Biomes.SNOWY_TAIGA;
        if (rainfall < 60.0F) {
            return profile == TerrainProfile.HIGHLANDS || profile == TerrainProfile.ROLLING_HILLS
                    ? Biomes.BADLANDS
                    : Biomes.DESERT;
        }
        if (rainfall < 155.0F) {
            return temperature > 18.0F ? Biomes.SAVANNA : Biomes.WINDSWEPT_HILLS;
        }
        if (karst && rainfall > 250.0F) {
            return temperature > 9.0F ? Biomes.SPARSE_JUNGLE : Biomes.WINDSWEPT_FOREST;
        }
        if (rainfall > 425.0F && temperature > 18.0F) return Biomes.JUNGLE;
        if (rainfall > 350.0F && temperature > 12.0F) {
            return profile == TerrainProfile.TIDAL_FLATS || profile == TerrainProfile.EMBAYMENTS
                    ? Biomes.MANGROVE_SWAMP
                    : Biomes.SWAMP;
        }
        if (temperature > 20.0F && rainfall > 250.0F) return Biomes.SPARSE_JUNGLE;
        if (temperature < 7.0F) return Biomes.TAIGA;
        if (rainfall > 270.0F) return Biomes.FOREST;
        return Biomes.PLAINS;
    }

    public static float iceSheetTemperature(float rainfall) {
        return -17.0F + 0.006F * rainfall;
    }

    public static boolean temperatureAtFreezing(float temperature, float rainfall) {
        return temperature < Math.min(0.0F, iceSheetTemperature(rainfall) + 6.0F);
    }

    public static boolean isOcean(TerrainProfile profile) {
        return profile == TerrainProfile.OCEAN || profile == TerrainProfile.DEEP_OCEAN;
    }

    private static ResourceKey<Biome> ocean(boolean deep, float temperature) {
        if (temperature < -5.0F) return deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN;
        if (temperature < 5.0F) return deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN;
        if (temperature > 22.0F && !deep) return Biomes.WARM_OCEAN;
        if (temperature > 14.0F) return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN;
        return deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
    }
}
