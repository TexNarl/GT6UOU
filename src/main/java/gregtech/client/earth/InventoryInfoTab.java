package gregtech.client.earth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** TFG-style calendar, climate and handbook tabs attached to inventory screens. */
public final class InventoryInfoTab extends AbstractWidget {
    private final Screen parent;
    private final Tab type;
    private final boolean active;

    private InventoryInfoTab(int x, int y, Screen parent, Tab type, boolean active) {
        super(x, y, 24, 24, Component.literal(type.title));
        this.parent = parent;
        this.type = type;
        this.active = active;
    }

    public static List<InventoryInfoTab> createTabs(int x, int y, Screen parent, Tab active) {
        List<InventoryInfoTab> tabs = new ArrayList<>(Tab.values().length);
        int offset = 0;
        for (Tab tab : Tab.values()) {
            tabs.add(new InventoryInfoTab(x, y + offset, parent, tab, tab == active));
            offset += 26;
        }
        return tabs;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (active) {
            return;
        }

        Screen next = switch (type) {
            case CALENDAR -> new EarthInfoScreen(parent, EarthInfoScreen.Mode.CALENDAR);
            case CLIMATE -> new EarthInfoScreen(parent, EarthInfoScreen.Mode.CLIMATE);
            case HANDBOOK -> new GeologyHandbookScreen(parent);
        };
        Minecraft.getInstance().setScreen(next);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int outer = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF8A8A8A;
        int inner = active ? 0xFFE2C46D : isHoveredOrFocused() ? 0xFFD6D6D6 : 0xFFB8B8B8;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF202020);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, outer);
        graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, inner);
        graphics.renderItem(type.icon, getX() + 4, getY() + 4);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public enum Tab {
        CALENDAR("Calendar", Items.CLOCK),
        CLIMATE("Climate", Items.SUNFLOWER),
        HANDBOOK("Geology handbook", Items.WRITABLE_BOOK);

        private final String title;
        private final ItemStack icon;

        Tab(String title, Item icon) {
            this.title = title;
            this.icon = new ItemStack(icon);
        }
    }
}
