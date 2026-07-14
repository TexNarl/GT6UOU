package gregtech.worldgen.earth.climate;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Temporal layer over the immutable Earth climate map.
 *
 * <p>The month modifiers, hemispheric triangle, daily swing and 66,000-tick
 * precipitation segments follow TFC's OverworldClimateModel. Worldgen never
 * consumes these values, so the same seed always creates the same terrain.</p>
 */
public final class EarthSeasonalClimate {
    public static final int DAYS_IN_MONTH = 8;
    public static final int MONTHS_IN_YEAR = 12;
    public static final long TICKS_IN_DAY = 24_000L;
    private static final long TICKS_IN_MONTH = DAYS_IN_MONTH * TICKS_IN_DAY;
    private static final long TICKS_IN_YEAR = MONTHS_IN_YEAR * TICKS_IN_MONTH;
    private static final long RAIN_SEGMENT_LENGTH = 66_000L;
    private static final float[] MONTH_MODIFIERS = {
            -1.0F, -0.866F, -0.5F, 0.0F, 0.5F, 0.866F,
            1.0F, 0.866F, 0.5F, 0.0F, -0.5F, -0.866F
    };
    private static final String[] SEASONS = {
            "winter", "winter", "spring", "spring", "spring", "summer",
            "summer", "summer", "autumn", "autumn", "autumn", "winter"
    };
    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };
    private static final String[] WEEKDAYS = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    private final long climateSeed;
    private final EarthClimateModel baseline;

    public EarthSeasonalClimate(long climateSeed, EarthClimateModel baseline) {
        this.climateSeed = climateSeed;
        this.baseline = baseline;
    }

    public TemporalSample sample(int blockX, int y, int blockZ, long gameTime) {
        float mean = baseline.averageTemperatureAtElevation(blockX, y, blockZ);
        float month = interpolatedMonthModifier(gameTime);
        float hemisphere = triangle(-18.0F, 0.0F,
                1.0F / (4.0F * EarthRegionScale.TEMPERATURE), blockZ - EarthRegionScale.TEMPERATURE / 2.0F);
        float monthly = month * hemisphere;

        long day = Math.floorDiv(gameTime, TICKS_IN_DAY);
        XoroshiroRandomSource dailyRandom = random(day, 1_986_239_412_341L);
        float fractionOfDay = Math.floorMod(gameTime, TICKS_IN_DAY) / (float) TICKS_IN_DAY;
        float hour = fractionOfDay < 0.5F
                ? map(fractionOfDay, 0.0F, 0.5F, -1.0F, 1.0F)
                : map(fractionOfDay, 0.5F, 1.0F, 1.0F, -1.0F);
        float daily = ((dailyRandom.nextFloat() - dailyRandom.nextFloat()) + 0.3F * hour) * 3.0F;

        float averageRain = baseline.averageRainfall(blockX, blockZ);
        float rainVariance = signedUnit(hash(blockX >> 9, blockZ >> 9)) * 0.28F;
        float year = Math.floorMod(gameTime, TICKS_IN_YEAR) / (float) TICKS_IN_YEAR;
        float instantRain = Math.clamp(triangle(
                rainVariance * averageRain,
                averageRain,
                1.0F,
                year + 0.75F
        ), 0.0F, 500.0F);
        float rainEvent = rainEvent(gameTime);
        boolean precipitating = rainEvent >= 0.0F
                && rainEvent <= Math.clamp(instantRain / 500.0F, 0.0F, 1.0F);
        return new TemporalSample(
                mean + monthly + daily,
                instantRain,
                precipitating ? rainEvent : 0.0F,
                season(gameTime),
                precipitating
        );
    }

    public String season(long gameTime) {
        int month = (int) Math.floorMod(Math.floorDiv(gameTime, TICKS_IN_MONTH), MONTHS_IN_YEAR);
        return SEASONS[month];
    }

    public CalendarSample calendar(long gameTime) {
        long totalDay = Math.floorDiv(gameTime, TICKS_IN_DAY);
        int month = (int) Math.floorMod(Math.floorDiv(gameTime, TICKS_IN_MONTH), MONTHS_IN_YEAR);
        int dayOfMonth = (int) Math.floorMod(totalDay, DAYS_IN_MONTH) + 1;
        int year = 1000 + (int) Math.floorDiv(totalDay, DAYS_IN_MONTH * MONTHS_IN_YEAR);
        int seasonMonth = switch (month) {
            case 11 -> 0;
            case 0 -> 1;
            case 1 -> 2;
            default -> month % 3;
        };
        String phase = switch (seasonMonth) {
            case 0 -> "Early";
            case 1 -> "Mid";
            default -> "Late";
        };
        String seasonName = SEASONS[month];
        String displaySeason = phase + " " + Character.toUpperCase(seasonName.charAt(0)) + seasonName.substring(1);
        return new CalendarSample(
                displaySeason,
                WEEKDAYS[(int) Math.floorMod(totalDay, WEEKDAYS.length)],
                MONTH_NAMES[month],
                dayOfMonth,
                year
        );
    }

    private float interpolatedMonthModifier(long gameTime) {
        int month = (int) Math.floorMod(Math.floorDiv(gameTime, TICKS_IN_MONTH), MONTHS_IN_YEAR);
        float delta = Math.floorMod(gameTime, TICKS_IN_MONTH) / (float) TICKS_IN_MONTH;
        return MONTH_MODIFIERS[month]
                + delta * (MONTH_MODIFIERS[(month + 1) % MONTHS_IN_YEAR] - MONTH_MODIFIERS[month]);
    }

    private float rainEvent(long gameTime) {
        long segmentId = Math.floorDiv(gameTime, RAIN_SEGMENT_LENGTH);
        long segmentStart = segmentId * RAIN_SEGMENT_LENGTH;
        XoroshiroRandomSource random = random(segmentId, 8_917_234_598_231_321L);
        int length = 12_000 + random.nextInt(12_001);
        int left = (int) (random.nextFloat() * (RAIN_SEGMENT_LENGTH - 12_000 - length));
        if (gameTime < segmentStart + left || gameTime > segmentStart + left + length) return -1.0F;
        int half = length / 2;
        float intensity = random(segmentId, 9_797_234_798_136_713L).nextFloat();
        float timeIntensity = 1.0F - Math.abs((segmentStart + left + half) - gameTime) / (float) half;
        return 0.5F * (intensity + timeIntensity);
    }

    private XoroshiroRandomSource random(long value, long salt) {
        return new XoroshiroRandomSource(mix64(value ^ climateSeed), salt);
    }

    private static float triangle(float amplitude, float midpoint, float frequency, float value) {
        return midpoint + amplitude * (Math.abs(4.0F * frequency * value + 1.0F
                - 4.0F * (float) Math.floor(frequency * value + 0.75F)) - 1.0F);
    }

    private static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
    }

    private static long hash(int x, int z) {
        return mix64(x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL);
    }

    private static float signedUnit(long value) {
        return (float) (((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0);
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    /** Keeps the climate-scale dependency explicit and independent from UI. */
    private static final class EarthRegionScale {
        private static final int TEMPERATURE = 20_000;
    }

    public record TemporalSample(
            float temperature,
            float rainfall,
            float precipitationIntensity,
            String season,
            boolean precipitating
    ) {
    }

    public record CalendarSample(
            String season,
            String weekday,
            String month,
            int dayOfMonth,
            int year
    ) {
    }
}
