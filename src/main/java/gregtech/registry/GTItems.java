package gregtech.registry;

import gregapi.code.BlockTypeDefinitions;

import gregapi.code.MineralResource;
import gregapi.code.OreBlockForm;
import gregapi.code.OreDropForm;
import gregapi.data.NaturalResourceList;
import gregapi.data.OreDropList;
import gregtech.GT6UOU;
import gregtech.dev.devtools.DayClockItem;
import gregtech.dev.devtools.GeologyHandbookItem;
import gregtech.dev.devtools.OreRevealToolItem;
import gregtech.dev.devtools.OreScannerItem;
import gregtech.dev.devtools.StrataCuttingToolItem;
import gregtech.item.GeneratedBlockItem;
import gregtech.item.GeneratedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GTItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GT6UOU.MODID);

    public static final Map<BlockTypeDefinitions.RockType, DeferredItem<BlockItem>> ROCK_ITEMS = new LinkedHashMap<>();
    public static final Map<BlockTypeDefinitions.ClayType, DeferredItem<BlockItem>> CLAY_ITEMS = new LinkedHashMap<>();
    public static final Map<BlockTypeDefinitions.SandType, DeferredItem<BlockItem>> SAND_ITEMS = new LinkedHashMap<>();
    public static final Map<BlockTypeDefinitions.OtherType, DeferredItem<BlockItem>> OTHER_ITEMS = new LinkedHashMap<>();
    public static final Map<OreBlockForm, Map<MineralResource, DeferredItem<BlockItem>>> ORE_FORM_ITEMS = new EnumMap<>(OreBlockForm.class);
    public static final Map<MineralResource, DeferredItem<BlockItem>> ORE_ITEMS = new LinkedHashMap<>();
    public static final Map<OreDropForm, Map<MineralResource, DeferredItem<Item>>> ORE_DROP_ITEMS = new EnumMap<>(OreDropForm.class);
    public static final Map<MineralResource, DeferredItem<Item>> POOR_RAW_ORE_ITEMS = new LinkedHashMap<>();
    public static final Map<MineralResource, DeferredItem<Item>> RAW_ORE_ITEMS = new LinkedHashMap<>();
    public static final Map<MineralResource, DeferredItem<Item>> RICH_RAW_ORE_ITEMS = new LinkedHashMap<>();
    public static final Map<MineralResource, DeferredItem<Item>> CRYSTAL_ITEMS = new LinkedHashMap<>();

    public static final DeferredItem<BucketItem> RAW_OIL_BUCKET = ITEMS.register(
            "raw_oil_bucket",
            () -> new BucketItem(GTFluids.RAW_OIL.get(), new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1))
    );

    public static final DeferredItem<Item> STRATA_CUTTING_TOOL = ITEMS.register(
            "strata_cutting_tool",
            () -> new StrataCuttingToolItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> DAY_CLOCK = ITEMS.register(
            "day_clock",
            () -> new DayClockItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> MINERAL_SCANNER = ITEMS.register(
            "mineral_scanner",
            () -> new OreScannerItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> ORE_REVEAL_TOOL = ITEMS.register(
            "ore_reveal_tool",
            () -> new OreRevealToolItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> GEOLOGY_HANDBOOK = ITEMS.register(
            "geology_handbook",
            () -> new GeologyHandbookItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<BlockItem> SURFACE_STICK = ITEMS.register(
            "surface_stick",
            () -> new GeneratedBlockItem(GTBlocks.SURFACE_STICK.get(), "Surface Stick", new Item.Properties())
    );

    public static final DeferredItem<BlockItem> SURFACE_ROCK = ITEMS.register(
            "surface_rock",
            () -> new GeneratedBlockItem(GTBlocks.SURFACE_ROCK.get(), "Surface Rock", new Item.Properties())
    );

    static {
        ORE_DROP_ITEMS.put(OreDropForm.POOR_RAW_MATERIAL, POOR_RAW_ORE_ITEMS);
        ORE_DROP_ITEMS.put(OreDropForm.RAW_MATERIAL, RAW_ORE_ITEMS);
        ORE_DROP_ITEMS.put(OreDropForm.RICH_RAW_MATERIAL, RICH_RAW_ORE_ITEMS);
        ORE_DROP_ITEMS.put(OreDropForm.CRYSTAL, CRYSTAL_ITEMS);

        for (MineralResource ore : NaturalResourceList.ORES) {
            if (OreDropList.dropForm(ore) == OreDropForm.CRYSTAL) {
                continue;
            }

            registerOreDropItem(ore, OreDropForm.POOR_RAW_MATERIAL, POOR_RAW_ORE_ITEMS);
            registerOreDropItem(ore, OreDropForm.RAW_MATERIAL, RAW_ORE_ITEMS);
            registerOreDropItem(ore, OreDropForm.RICH_RAW_MATERIAL, RICH_RAW_ORE_ITEMS);
        }

        for (MineralResource ore : OreDropList.CRYSTAL_ORES) {
            registerOreDropItem(ore, OreDropForm.CRYSTAL, CRYSTAL_ITEMS);
        }
    }

    private GTItems() {
    }

    public static DeferredItem<BlockItem> registerRockItem(BlockTypeDefinitions.RockType rock, DeferredBlock<? extends Block> block) {
        DeferredItem<BlockItem> item = registerBlockItem(rock.id(), block, rock.englishName());
        ROCK_ITEMS.put(rock, item);
        return item;
    }

    public static DeferredItem<BlockItem> registerClayItem(BlockTypeDefinitions.ClayType clay, DeferredBlock<? extends Block> block) {
        DeferredItem<BlockItem> item = registerBlockItem(clay.id(), block, clay.englishName());
        CLAY_ITEMS.put(clay, item);
        return item;
    }

    public static DeferredItem<BlockItem> registerSandItem(BlockTypeDefinitions.SandType sand, DeferredBlock<? extends Block> block) {
        DeferredItem<BlockItem> item = registerBlockItem(sand.id(), block, sand.englishName());
        SAND_ITEMS.put(sand, item);
        return item;
    }

    public static DeferredItem<BlockItem> registerOtherItem(BlockTypeDefinitions.OtherType other, DeferredBlock<? extends Block> block) {
        DeferredItem<BlockItem> item = registerBlockItem(other.id(), block, other.englishName());
        OTHER_ITEMS.put(other, item);
        return item;
    }

    public static DeferredItem<BlockItem> registerOreItem(MineralResource ore, DeferredBlock<? extends Block> block) {
        return registerOreItem(ore, OreBlockForm.NORMAL, block);
    }

    public static DeferredItem<BlockItem> registerOreItem(MineralResource ore, OreBlockForm form, DeferredBlock<? extends Block> block) {
        DeferredItem<BlockItem> item = registerBlockItem(GTBlocks.oreBlockId(ore, form), block, form.englishName(ore));
        ORE_FORM_ITEMS.computeIfAbsent(form, ignored -> new LinkedHashMap<>()).put(ore, item);

        if (form == OreBlockForm.NORMAL) {
            ORE_ITEMS.put(ore, item);
        }

        return item;
    }

    private static DeferredItem<BlockItem> registerBlockItem(String id, DeferredBlock<? extends Block> block, String englishName) {
        return ITEMS.register(id, () -> new GeneratedBlockItem(block.get(), englishName, new Item.Properties()));
    }

    private static DeferredItem<Item> registerGeneratedItem(String id, String englishName) {
        return ITEMS.register(id, () -> new GeneratedItem(englishName, new Item.Properties()));
    }

    private static void registerOreDropItem(
            MineralResource ore,
            OreDropForm form,
            Map<MineralResource, DeferredItem<Item>> target
    ) {
        target.put(ore, registerGeneratedItem(form.itemId(ore), form.englishName(ore)));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
