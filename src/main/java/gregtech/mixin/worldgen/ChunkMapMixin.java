package gregtech.mixin.worldgen;

import com.mojang.datafixers.DataFixer;
import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.EarthRandomStateAccess;
import gregtech.worldgen.earth.GTEarthChunkGenerator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Initializes Earth seed state at the same lifecycle point where Minecraft has
 * created the level's RandomState. This avoids fixed/debug seeds and makes the
 * state available to later structure and terrain stages.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Mutable
    @Shadow
    @Final
    private WorldGenContext worldGenContext;

    @Shadow
    @Final
    private RandomState randomState;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gt6uou$initializeEarthState(
            ServerLevel level,
            LevelStorageSource.LevelStorageAccess levelStorageAccess,
            DataFixer fixerUpper,
            StructureTemplateManager structureManager,
            Executor dispatcher,
            BlockableEventLoop<Runnable> mainThreadExecutor,
            LightChunkGetter lightChunk,
            ChunkGenerator generator,
            ChunkProgressListener progressListener,
            ChunkStatusUpdateListener chunkStatusListener,
            Supplier<DimensionDataStorage> overworldDataStorage,
            int viewDistance,
            boolean sync,
            CallbackInfo ci
    ) {
        if (!(generator instanceof GTEarthChunkGenerator earthGenerator)) {
            return;
        }

        GTEarthChunkGenerator runtimeGenerator = earthGenerator;
        EarthGeneratorState existingState = earthGenerator.earthState();
        if (existingState != null && !existingState.matches(level.dimension(), level.getSeed())) {
            runtimeGenerator = earthGenerator.copyForLevel();
            worldGenContext = new WorldGenContext(
                    worldGenContext.level(),
                    runtimeGenerator,
                    worldGenContext.structureManager(),
                    worldGenContext.lightEngine(),
                    worldGenContext.mainThreadMailBox()
            );
        }

        EarthGeneratorState state = runtimeGenerator.initializeForLevel(level);
        ((EarthRandomStateAccess) (Object) randomState).gt6uou$setEarthState(state);
    }
}
