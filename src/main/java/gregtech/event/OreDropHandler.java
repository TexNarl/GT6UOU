package gregtech.event;

import gregapi.code.MineralResource;
import gregapi.code.OreBlockForm;
import gregapi.code.OreDropForm;
import gregapi.data.OreDropList;
import gregapi.data.NaturalResourceList;
import gregtech.GT6UOU;
import gregtech.registry.GTItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime drop rules for generated ore blocks.
 *
 * <p>Ore blocks drop resource items like modern vanilla ores. Compact raw blocks
 * are storage blocks and drop themselves.</p>
 */
public final class OreDropHandler {
    private OreDropHandler() {
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());

        if (!GT6UOU.MODID.equals(blockKey.getNamespace())) {
            return;
        }

        String blockId = blockKey.getPath();
        OreBlockForm blockForm = OreBlockForm.fromBlockId(blockId);
        String oreId = OreBlockForm.mineralIdFromBlockId(blockId);
        Optional<MineralResource> ore = NaturalResourceList.byId(oreId);

        if (ore.isEmpty()) {
            return;
        }

        if (hasSilkTouch(event)) {
            List<ItemStack> silkTouchDrops = silkTouchDrops(blockForm, ore.get());

            if (!silkTouchDrops.isEmpty()) {
                event.getDrops().clear();
                for (ItemStack stack : silkTouchDrops) {
                    event.getDrops().add(new ItemEntity(
                            event.getLevel(),
                            event.getPos().getX() + 0.5D,
                            event.getPos().getY() + 0.5D,
                            event.getPos().getZ() + 0.5D,
                            stack
                    ));
                }
            }

            return;
        }

        List<ItemStack> dropStacks = dropStacksFor(event, blockId, blockForm, ore.get());

        if (dropStacks.isEmpty()) {
            return;
        }

        event.getDrops().clear();
        for (ItemStack stack : dropStacks) {
            event.getDrops().add(new ItemEntity(
                    event.getLevel(),
                    event.getPos().getX() + 0.5D,
                    event.getPos().getY() + 0.5D,
                    event.getPos().getZ() + 0.5D,
                    stack
            ));
        }
    }

    private static List<ItemStack> dropStacksFor(BlockDropsEvent event, String blockId, OreBlockForm blockForm, MineralResource ore) {
        if (blockForm == OreBlockForm.RAW_BLOCK && blockId.equals(OreBlockForm.RAW_BLOCK.blockId(ore))) {
            Map<MineralResource, DeferredItem<net.minecraft.world.item.BlockItem>> rawBlockItems =
                    GTItems.ORE_FORM_ITEMS.get(OreBlockForm.RAW_BLOCK);

            if (rawBlockItems == null || !rawBlockItems.containsKey(ore)) {
                return List.of();
            }

            return List.of(new ItemStack(rawBlockItems.get(ore).get()));
        }

        OreDropForm dropForm = OreDropList.dropForm(ore);

        if (dropForm == OreDropForm.RAW_MATERIAL) {
            OreDropList.OreDropChoice choice = selectChoice(
                    OreDropList.rawDropChoicesForOreBlockForm(blockForm),
                    event.getLevel().getRandom().nextInt()
            );
            List<ItemStack> drops = new ArrayList<>();

            for (OreDropForm rawDropForm : choice.drops()) {
                DeferredItem<Item> item = rawDropItems(rawDropForm).get(ore);

                if (item != null) {
                    drops.add(new ItemStack(item.get()));
                }
            }

            return drops;
        }

        if (dropForm == OreDropForm.CRYSTAL) {
            DeferredItem<Item> item = GTItems.CRYSTAL_ITEMS.get(ore);

            if (item != null) {
                return List.of(new ItemStack(item.get(), 2));
            }
        }

        return List.of();
    }

    private static boolean hasSilkTouch(BlockDropsEvent event) {
        ItemStack tool = event.getTool();

        if (tool.isEmpty()) {
            return false;
        }

        return tool.getEnchantmentLevel(event.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH)) > 0;
    }

    private static List<ItemStack> silkTouchDrops(OreBlockForm blockForm, MineralResource ore) {
        Map<MineralResource, DeferredItem<net.minecraft.world.item.BlockItem>> blockItems =
                GTItems.ORE_FORM_ITEMS.get(blockForm);

        if (blockItems == null || !blockItems.containsKey(ore)) {
            return List.of();
        }

        return List.of(new ItemStack(blockItems.get(ore).get()));
    }

    private static OreDropList.OreDropChoice selectChoice(List<OreDropList.OreDropChoice> choices, int randomValue) {
        int totalWeight = choices.stream()
                .mapToInt(OreDropList.OreDropChoice::weight)
                .sum();
        int roll = Math.floorMod(randomValue, totalWeight);

        for (OreDropList.OreDropChoice choice : choices) {
            roll -= choice.weight();

            if (roll < 0) {
                return choice;
            }
        }

        return choices.getLast();
    }

    private static Map<MineralResource, DeferredItem<Item>> rawDropItems(OreDropForm dropForm) {
        return switch (dropForm) {
            case POOR_RAW_MATERIAL -> GTItems.POOR_RAW_ORE_ITEMS;
            case RAW_MATERIAL -> GTItems.RAW_ORE_ITEMS;
            case RICH_RAW_MATERIAL -> GTItems.RICH_RAW_ORE_ITEMS;
            case CRYSTAL -> GTItems.CRYSTAL_ITEMS;
        };
    }
}
