package gregtech.registry;

import gregapi.data.RocksList;

import gregapi.code.BlockTypeDefinitions;

import gregapi.code.MineralResource;
import gregapi.code.OreBlockForm;
import gregapi.code.OreDropForm;
import gregapi.data.OreDropList;
import gregapi.data.NaturalResourceList;
import gregtech.GT6UOU;
import gregtech.block.SurfaceLooseBlock;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GTBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GT6UOU.MODID);

    public static final Map<BlockTypeDefinitions.RockType, DeferredBlock<Block>> ROCK_BLOCKS = new LinkedHashMap<>();

    public static final Map<BlockTypeDefinitions.ClayType, DeferredBlock<Block>> CLAY_BLOCKS = new LinkedHashMap<>();

    public static final Map<BlockTypeDefinitions.SandType, DeferredBlock<ColoredFallingBlock>> SAND_BLOCKS = new LinkedHashMap<>();

    public static final Map<BlockTypeDefinitions.OtherType, DeferredBlock<Block>> OTHER_BLOCKS = new LinkedHashMap<>();

    public static final Map<OreBlockForm, Map<MineralResource, DeferredBlock<Block>>> ORE_FORM_BLOCKS = new EnumMap<>(OreBlockForm.class);

    public static final Map<MineralResource, DeferredBlock<Block>> ORE_BLOCKS = new LinkedHashMap<>();

    public static final DeferredBlock<LiquidBlock> RAW_OIL_BLOCK = BLOCKS.registerBlock(
            "raw_oil",
            properties -> new LiquidBlock(GTFluids.RAW_OIL.get(), properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()
    );

    public static final DeferredBlock<SurfaceLooseBlock> SURFACE_STICK = BLOCKS.registerBlock(
            "surface_stick",
            properties -> new SurfaceLooseBlock(SurfaceLooseBlock.Kind.STICK, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)
                    .noCollission()
                    .sound(SoundType.WOOD)
                    .dynamicShape()
    );

    public static final DeferredBlock<SurfaceLooseBlock> SURFACE_ROCK = BLOCKS.registerBlock(
            "surface_rock",
            properties -> new SurfaceLooseBlock(SurfaceLooseBlock.Kind.ROCK, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)
                    .noCollission()
                    .sound(SoundType.STONE)
                    .dynamicShape()
    );

    static {
        for (BlockTypeDefinitions.RockType rock : RocksList.ROCKS) {
            DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(rock.id(), rock.stoneBlockProperties());
            GTItems.registerRockItem(rock, block);

            ROCK_BLOCKS.put(rock, block);
        }

        for (BlockTypeDefinitions.ClayType clay : RocksList.CLAYS) {
            DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(clay.id(), clay.clayBlockProperties());
            GTItems.registerClayItem(clay, block);

            CLAY_BLOCKS.put(clay, block);
        }

        for (BlockTypeDefinitions.SandType sand : RocksList.SANDS) {
            DeferredBlock<ColoredFallingBlock> block = BLOCKS.registerBlock(
                    sand.id(),
                    properties -> new ColoredFallingBlock(new ColorRGBA(sand.opaqueTintColor()), properties),
                    sand.sandBlockProperties()
            );
            GTItems.registerSandItem(sand, block);

            SAND_BLOCKS.put(sand, block);
        }

        for (BlockTypeDefinitions.OtherType other : RocksList.OTHERS) {
            DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(other.id(), other.dirtBlockProperties());
            GTItems.registerOtherItem(other, block);

            OTHER_BLOCKS.put(other, block);
        }

        for (OreBlockForm form : OreBlockForm.values()) {
            ORE_FORM_BLOCKS.put(form, new LinkedHashMap<>());
        }

        for (MineralResource ore : NaturalResourceList.ORES) {
            for (OreBlockForm form : oreBlockForms(ore)) {
                String blockId = oreBlockId(ore, form);
                DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(blockId, oreBlockProperties(form));
                GTItems.registerOreItem(ore, form, block);

                ORE_FORM_BLOCKS.get(form).put(ore, block);
                if (form == OreBlockForm.NORMAL) {
                    ORE_BLOCKS.put(ore, block);
                }
            }
        }
    }

    private GTBlocks() {
    }

    public static String oreBlockId(MineralResource ore) {
        return oreBlockId(ore, OreBlockForm.NORMAL);
    }

    public static String oreBlockId(MineralResource ore, OreBlockForm form) {
        return form.blockId(ore);
    }

    private static OreBlockForm[] oreBlockForms(MineralResource ore) {
        if (OreDropList.dropForm(ore) == OreDropForm.CRYSTAL) {
            return new OreBlockForm[] {OreBlockForm.NORMAL};
        }

        return OreBlockForm.values();
    }

    private static BlockBehaviour.Properties oreBlockProperties(OreBlockForm form) {
        if (form.isCompactRawBlock()) {
            return BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK);
        }

        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
