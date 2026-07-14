package gregtech.mixin.worldgen;

import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.EarthRandomStateAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RandomState.class)
public abstract class RandomStateMixin implements EarthRandomStateAccess {
    @Unique
    @Nullable
    private EarthGeneratorState gt6uou$earthState;

    @Override
    public void gt6uou$setEarthState(@Nullable EarthGeneratorState state) {
        gt6uou$earthState = state;
    }

    @Override
    @Nullable
    public EarthGeneratorState gt6uou$getEarthState() {
        return gt6uou$earthState;
    }
}
