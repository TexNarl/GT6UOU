package gregtech.worldgen.earth;

import gregtech.worldgen.resources.OreVeinWorldgen;
import gregtech.worldgen.resources.RawOilSproutWorldgen;
import gregtech.worldgen.resources.SurfaceDepositWorldgen;
import gregtech.worldgen.surface.SurfaceLooseObjectWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Explicit post-surface resource order for {@code gt6uou:earth}.
 *
 * <p>Earth host rocks are already final when this coordinator runs. It must
 * therefore place deposits and ores directly without invoking the Overworld's
 * vanilla-stone replacement compatibility scan.</p>
 */
public final class EarthResourceWorldgen {
    private EarthResourceWorldgen() {
    }

    public static int generate(
            WorldGenLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            EarthGeneratorState state
    ) {
        int generatedBlocks = 0;
        // Deposits intentionally run before ores: clay/sand/turf retain their
        // established priority and veins only replace host-rock blocks left
        // behind by those deposits.
        generatedBlocks += SurfaceDepositWorldgen.generateEarth(level, chunk, pos, state);
        generatedBlocks += OreVeinWorldgen.generateEarthOreVeins(
                level,
                chunk,
                pos,
                state.stoneLayerSampler(),
                state.heightFiller()::sampleBlockHeight
        );
        generatedBlocks += RawOilSproutWorldgen.generate(level, chunk, pos);
        generatedBlocks += SurfaceLooseObjectWorldgen.generate(level, chunk, pos);
        return generatedBlocks;
    }
}
