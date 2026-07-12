package gregtech.mixin;

import gregtech.GT6UOU;
import gregtech.worldgen.RawOilSproutWorldgen;
import gregtech.worldgen.StoneLayerWorldgen;
import gregtech.worldgen.SurfaceDepositWorldgen;
import gregtech.worldgen.SurfaceLooseObjectWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void gt6uou$applyBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            CallbackInfo ci
    ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int generatedBlocks = 0;

        generatedBlocks += SurfaceDepositWorldgen.generate(level, chunk, pos);
        generatedBlocks += StoneLayerWorldgen.generate(level, chunk, pos);
        generatedBlocks += RawOilSproutWorldgen.generate(level, chunk, pos);
        generatedBlocks += SurfaceLooseObjectWorldgen.generate(level, chunk, pos);

        if (generatedBlocks > 0 && chunk instanceof LevelChunk levelChunk) {
            levelChunk.setUnsaved(true);
            GT6UOU.LOGGER.debug("Generated {} GT6UOU Overworld blocks in chunk {}", generatedBlocks, chunk.getPos());
        }
    }
}
