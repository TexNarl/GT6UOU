package gregtech.worldgen.earth;

import org.jetbrains.annotations.Nullable;

/**
 * Runtime bridge implemented by Minecraft's RandomState through mixin.
 * It lives outside the mixin package so transformed Minecraft classes may
 * safely expose it to normal world-generation code.
 */
public interface EarthRandomStateAccess {
    void gt6uou$setEarthState(@Nullable EarthGeneratorState state);

    @Nullable
    EarthGeneratorState gt6uou$getEarthState();
}
