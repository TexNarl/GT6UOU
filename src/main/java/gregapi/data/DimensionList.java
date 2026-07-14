package gregapi.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Registry keys for dimensions used by API data lists and the mod implementation.
 */
public final class DimensionList {
    public static final ResourceKey<Level> EARTH = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("gt6uou", "earth")
    );

    private DimensionList() {
    }

    public static boolean isEarthLike(Level level) {
        return isEarthLike(level.dimension());
    }

    public static boolean isEarthLike(WorldGenLevel level) {
        return isEarthLike(level.getLevel().dimension());
    }

    public static boolean isEarthLike(ResourceKey<Level> dimension) {
        return dimension.equals(Level.OVERWORLD) || dimension.equals(EARTH);
    }
}
