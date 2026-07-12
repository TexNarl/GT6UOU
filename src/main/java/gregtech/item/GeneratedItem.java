package gregtech.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GeneratedItem extends Item {
    private final String englishName;

    public GeneratedItem(String englishName, Properties properties) {
        super(properties);
        this.englishName = englishName;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(englishName);
    }
}
