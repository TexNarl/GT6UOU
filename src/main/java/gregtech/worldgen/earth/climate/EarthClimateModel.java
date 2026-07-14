/*
 * Climate field architecture follows TerraFirmaCraft/TerraFirmaGreg.
 * Those projects and their EUPL/LGPL licenses are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.climate;

import gregtech.worldgen.earth.region.EarthRegion;
import gregtech.worldgen.earth.region.EarthRegionGenerator;
import gregtech.worldgen.earth.region.EarthUnits;

/**
 * Continuous, time-invariant Earth climate sampled from the completed
 * regional map. Regional points are interpreted at exact 128-block grid
 * coordinates and bilinearly interpolated, matching TFC chunk climate data.
 *
 * <p>Seasons are intentionally not part of this class. They are a temporal
 * modifier over this mean annual baseline and can be added without changing
 * continent, biome or preview determinism.</p>
 */
public final class EarthClimateModel {
    public static final int SEA_LEVEL = 63;
    private static final float ELEVATION_COOLING_PER_BLOCK = 0.16225F;
    private static final float MAX_ELEVATION_COOLING = 17.822F;

    private final EarthRegionGenerator regionGenerator;

    public EarthClimateModel(EarthRegionGenerator regionGenerator) {
        this.regionGenerator = regionGenerator;
    }

    public ClimateSample sample(int blockX, int blockZ) {
        double exactGridX = EarthUnits.blockToGridExact(blockX);
        double exactGridZ = EarthUnits.blockToGridExact(blockZ);
        int gridX = (int) Math.floor(exactGridX);
        int gridZ = (int) Math.floor(exactGridZ);
        float deltaX = (float) (exactGridX - gridX);
        float deltaZ = (float) (exactGridZ - gridZ);

        EarthRegion.Point point00 = regionGenerator.getOrCreatePoint(gridX, gridZ);
        EarthRegion.Point point01 = regionGenerator.getOrCreatePoint(gridX, gridZ + 1);
        EarthRegion.Point point10 = regionGenerator.getOrCreatePoint(gridX + 1, gridZ);
        EarthRegion.Point point11 = regionGenerator.getOrCreatePoint(gridX + 1, gridZ + 1);

        return new ClimateSample(
                bilerp(
                        point00.averageTemperature(), point01.averageTemperature(),
                        point10.averageTemperature(), point11.averageTemperature(),
                        deltaX, deltaZ
                ),
                Math.clamp(bilerp(
                        point00.averageRainfall(), point01.averageRainfall(),
                        point10.averageRainfall(), point11.averageRainfall(),
                        deltaX, deltaZ
                ), 0.0F, 500.0F)
        );
    }

    public float averageTemperature(int blockX, int blockZ) {
        return sample(blockX, blockZ).averageTemperature();
    }

    public float averageTemperatureAtElevation(int blockX, int y, int blockZ) {
        float cooling = Math.clamp(
                (y - SEA_LEVEL) * ELEVATION_COOLING_PER_BLOCK,
                0.0F,
                MAX_ELEVATION_COOLING
        );
        return averageTemperature(blockX, blockZ) - cooling;
    }

    public float averageRainfall(int blockX, int blockZ) {
        return sample(blockX, blockZ).averageRainfall();
    }

    private static float bilerp(
            float value00,
            float value01,
            float value10,
            float value11,
            float deltaX,
            float deltaZ
    ) {
        float north = value00 + deltaX * (value10 - value00);
        float south = value01 + deltaX * (value11 - value01);
        return north + deltaZ * (south - north);
    }

    public record ClimateSample(float averageTemperature, float averageRainfall) {
    }
}
