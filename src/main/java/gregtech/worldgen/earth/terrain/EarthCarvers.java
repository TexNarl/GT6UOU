/*
 * Cave/canyon integration follows TerraFirmaCraft's TFCCaveCarver,
 * TFCCanyonCarver and CarverHelpers architecture. TerraFirmaCraft and
 * TerraFirmaGreg are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.terrain;

import com.mojang.serialization.Codec;
import gregtech.GT6UOU;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Earth carver types reuse Mojang's proven cave/canyon geometry but replace
 * their block-writing policy with the same aquifer-aware policy used by TFC.
 * Shape probabilities and dimensions remain data in configured-carver JSON.
 */
public final class EarthCarvers {
    private static final DeferredRegister<WorldCarver<?>> CARVERS =
            DeferredRegister.create(Registries.CARVER, GT6UOU.MODID);

    static {
        CARVERS.register("earth_cave", () -> new EarthCaveCarver(CaveCarverConfiguration.CODEC));
        CARVERS.register("earth_canyon", () -> new EarthCanyonCarver(CanyonCarverConfiguration.CODEC));
    }

    private EarthCarvers() {
    }

    public static void register(IEventBus modEventBus) {
        CARVERS.register(modEventBus);
    }

    private static final class EarthCaveCarver extends CaveWorldCarver {
        private EarthCaveCarver(Codec<CaveCarverConfiguration> codec) {
            super(codec);
        }

        @Override
        protected boolean carveBlock(
                CarvingContext context,
                CaveCarverConfiguration config,
                ChunkAccess chunk,
                Function<BlockPos, Holder<Biome>> biomeAccessor,
                CarvingMask carvingMask,
                BlockPos.MutableBlockPos pos,
                BlockPos.MutableBlockPos checkPos,
                Aquifer aquifer,
                MutableBoolean reachedSurface
        ) {
            return EarthCarvers.carveBlock(
                    context, config, chunk, pos, aquifer, this::canReplaceBlock
            );
        }
    }

    private static final class EarthCanyonCarver extends CanyonWorldCarver {
        private EarthCanyonCarver(Codec<CanyonCarverConfiguration> codec) {
            super(codec);
        }

        @Override
        protected boolean carveBlock(
                CarvingContext context,
                CanyonCarverConfiguration config,
                ChunkAccess chunk,
                Function<BlockPos, Holder<Biome>> biomeAccessor,
                CarvingMask carvingMask,
                BlockPos.MutableBlockPos pos,
                BlockPos.MutableBlockPos checkPos,
                Aquifer aquifer,
                MutableBoolean reachedSurface
        ) {
            return EarthCarvers.carveBlock(
                    context, config, chunk, pos, aquifer, this::canReplaceBlock
            );
        }
    }

    private static <C extends CarverConfiguration> boolean carveBlock(
            CarvingContext context,
            C config,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            Aquifer aquifer,
            BiPredicate<C, BlockState> canReplaceBlock
    ) {
        BlockState current = chunk.getBlockState(pos);
        if (!canReplaceBlock.test(config, current) && !isDebugEnabled(config)) {
            return false;
        }

        BlockState carved = carveState(context, config, pos, aquifer);
        if (carved == null) {
            return false;
        }

        chunk.setBlockState(pos, carved, false);
        if (aquifer.shouldScheduleFluidUpdate() && !carved.getFluidState().isEmpty()) {
            chunk.markPosForPostprocessing(pos);
        }
        return true;
    }

    private static BlockState carveState(
            CarvingContext context,
            CarverConfiguration config,
            BlockPos pos,
            Aquifer aquifer
    ) {
        if (pos.getY() <= config.lavaLevel.resolveY(context)) {
            return Blocks.LAVA.defaultBlockState();
        }

        BlockState state = aquifer.computeSubstance(
                new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()),
                0.0D
        );
        if (state == null) {
            return isDebugEnabled(config) ? config.debugSettings.getBarrierState() : null;
        }
        return isDebugEnabled(config) ? debugState(config, state) : state;
    }

    private static BlockState debugState(CarverConfiguration config, BlockState state) {
        if (state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR)) {
            return config.debugSettings.getAirState();
        }
        if (state.is(Blocks.WATER)) {
            BlockState debug = config.debugSettings.getWaterState();
            return debug.hasProperty(BlockStateProperties.WATERLOGGED)
                    ? debug.setValue(BlockStateProperties.WATERLOGGED, true)
                    : debug;
        }
        return state.is(Blocks.LAVA) ? config.debugSettings.getLavaState() : state;
    }

    private static boolean isDebugEnabled(CarverConfiguration config) {
        return config.debugSettings.isDebugMode();
    }
}
