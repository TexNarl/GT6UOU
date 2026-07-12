package gregtech.item;

import gregapi.lang.GTLocalization;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class GeneratedBlockItem extends BlockItem {
    private final Component generatedName;

    public GeneratedBlockItem(Block block, String englishName, Properties properties) {
        super(block, properties);
        this.generatedName = GTLocalization.literal(englishName);
    }

    @Override
    public Component getName(ItemStack stack) {
        return generatedName;
    }
}
