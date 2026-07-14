package gregtech.block;

import gregapi.code.BlockTypeDefinitions;
import gregtech.registry.GTItems;
import gregapi.data.DimensionList;
import gregtech.worldgen.earth.GTEarthChunkGenerator;
import gregtech.worldgen.geology.StoneLayerStackSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public class SurfaceLooseBlock extends Block {
    private static final VoxelShape STICK_SHAPE = Shapes.or(
            Block.box(3.0, 0.0, 2.0, 5.0, 2.0, 6.0),
            Block.box(4.0, 0.0, 6.0, 6.0, 2.0, 10.0),
            Block.box(5.0, 0.0, 10.0, 7.0, 2.0, 15.0)
    );
    private static final VoxelShape ROCK_SHAPE = Block.box(7.0, 0.0, 6.0, 12.0, 3.0, 13.0);

    private final Kind kind;

    public SurfaceLooseBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == Kind.STICK ? STICK_SHAPE : ROCK_SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);

        return isValidSupportBlock(belowState)
                && belowState.isFaceSturdy(level, belowPos, Direction.UP);
    }

    public static boolean isValidSupportBlock(BlockState state) {
        return state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = pickupStack(level, pos);

        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }

        level.removeBlock(pos, false);
        return InteractionResult.SUCCESS;
    }

    public ItemStack pickupStack(Level level, BlockPos pos) {
        return switch (kind) {
            case STICK -> new ItemStack(Items.STICK);
            case ROCK -> rockStack(level, pos);
        };
    }

    private static ItemStack rockStack(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return new ItemStack(Items.COBBLESTONE);
        }

        int surfaceY = Math.max(level.getMinBuildHeight(), pos.getY() - 1);
        StoneLayerStackSampler sampler = stoneLayerSampler(serverLevel);
        String rockId = sampler.sampleRockId(pos.getX(), surfaceY - 4, pos.getZ(), surfaceY);

        Optional<BlockTypeDefinitions.RockType> rockType = GTItems.ROCK_ITEMS.keySet().stream()
                .filter(rock -> rock.id().equals(rockId))
                .findFirst();

        return rockType
                .map(rock -> new ItemStack(GTItems.ROCK_ITEMS.get(rock).get()))
                .orElseGet(() -> new ItemStack(Items.COBBLESTONE));
    }

    private static StoneLayerStackSampler stoneLayerSampler(ServerLevel level) {
        if (level.dimension().equals(DimensionList.EARTH)
                && level.getChunkSource().getGenerator() instanceof GTEarthChunkGenerator generator
                && generator.earthState() != null) {
            return generator.earthState().stoneLayerSampler();
        }

        return new StoneLayerStackSampler(level.getSeed());
    }

    public enum Kind {
        STICK,
        ROCK
    }
}
