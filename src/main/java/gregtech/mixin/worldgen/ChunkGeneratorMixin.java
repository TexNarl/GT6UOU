package gregtech.mixin.worldgen;

import gregtech.GT6UOU;
import gregapi.data.DimensionList;
import gregtech.worldgen.resources.RawOilSproutWorldgen;
import gregtech.worldgen.resources.OreVeinWorldgen;
import gregtech.worldgen.geology.StoneLayerWorldgen;
import gregtech.worldgen.resources.SurfaceDepositWorldgen;
import gregtech.worldgen.surface.SurfaceLooseObjectWorldgen;
import gregtech.worldgen.earth.EarthResourceWorldgen;
import gregtech.worldgen.earth.GTEarthChunkGenerator;
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

        if (level.getLevel().dimension().equals(DimensionList.EARTH)) {
            if (!(level.getLevel().getChunkSource().getGenerator() instanceof GTEarthChunkGenerator earthGenerator)
                    || earthGenerator.earthState() == null) {
                throw new IllegalStateException("GT6UOU Earth resource generation has no initialized Earth generator state");
            }
            generatedBlocks += EarthResourceWorldgen.generate(level, chunk, pos, earthGenerator.earthState());
        } else {
            // Keep the established Overworld order unchanged.
            generatedBlocks += SurfaceDepositWorldgen.generate(level, chunk, pos);
            generatedBlocks += StoneLayerWorldgen.generate(level, chunk, pos);
            generatedBlocks += OreVeinWorldgen.generateOverworldOreVeins(level, chunk, pos);
            generatedBlocks += RawOilSproutWorldgen.generate(level, chunk, pos);
            generatedBlocks += SurfaceLooseObjectWorldgen.generate(level, chunk, pos);
        }

        if (generatedBlocks > 0 && chunk instanceof LevelChunk levelChunk) {
            levelChunk.setUnsaved(true);
            GT6UOU.LOGGER.debug("Generated {} GT6UOU resource blocks in {} chunk {}",
                    generatedBlocks, level.getLevel().dimension().location(), chunk.getPos());
        }
    }
}
