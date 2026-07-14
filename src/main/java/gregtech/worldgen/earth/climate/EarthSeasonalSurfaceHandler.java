package gregtech.worldgen.earth.climate;

import gregapi.data.DimensionList;
import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.GTEarthChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Lightweight visible consumer of the TFC-style temporal climate.
 * Loaded Earth terrain accumulates snow/ice during freezing precipitation and
 * thaws again in warm seasons. It never loads chunks and samples only a small
 * number of columns around current players once per second.
 */
public final class EarthSeasonalSurfaceHandler {
    private EarthSeasonalSurfaceHandler() {
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(DimensionList.EARTH)
                || Math.floorMod(level.getGameTime(), 20L) != 0L
                || !(level.getChunkSource().getGenerator() instanceof GTEarthChunkGenerator generator)
                || generator.earthState() == null) {
            return;
        }

        EarthGeneratorState state = generator.earthState();
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        for (var player : level.players()) {
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = player.getBlockX() + level.random.nextInt(65) - 32;
                int z = player.getBlockZ() + level.random.nextInt(65) - 32;
                sample.set(x, level.getMinBuildHeight(), z);
                if (!level.hasChunkAt(sample)) continue;

                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample);
                BlockPos ground = top.below();
                if (!level.canSeeSky(top)) continue;
                var climate = state.seasonalClimate().sample(
                        x, ground.getY(), z, level.getDayTime()
                );

                if (climate.temperature() <= 0.0F && climate.precipitating()) {
                    if (level.getBlockState(ground).is(Blocks.WATER)) {
                        level.setBlockAndUpdate(ground, Blocks.ICE.defaultBlockState());
                    } else if (level.getBlockState(top).isAir()
                            && Blocks.SNOW.defaultBlockState().canSurvive(level, top)) {
                        level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
                    }
                } else if (climate.temperature() > 2.0F) {
                    if (level.getBlockState(ground).is(Blocks.ICE)) {
                        level.setBlockAndUpdate(ground, Blocks.WATER.defaultBlockState());
                    } else if (level.getBlockState(ground).is(Blocks.SNOW)) {
                        level.removeBlock(ground, false);
                    }
                }
            }
        }
    }
}
