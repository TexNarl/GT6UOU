package gregtech.client.earth;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class GeologyHandbookScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 210;
    private static final int PANEL_COLOR = 0xEE151617;
    private static final int SIDE_COLOR = 0xEE202226;
    private static final int BORDER_COLOR = 0xFF565C66;
    private static final int TEXT_COLOR = 0xFFEDEFF2;
    private static final int MUTED_TEXT_COLOR = 0xFFA8ADB7;
    private static final int ACCENT_COLOR = 0xFFFFD36A;
    private static final int SECTION_ACTIVE_COLOR = 0xFF343943;
    private static final int SCROLLBAR_TRACK_COLOR = 0x663A3D42;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFF8B929E;

    private static final List<String> WORLDGEN_TEXT = List.of(
            "World Generation",
            "",
            "The Overworld and GT Earth use GT6UOU geology. Underground terrain is replaced by GT6UOU rock layers.",
            "",
            "Rock layers",
            "- The world is split into large geological regions.",
            "- Each region chooses one rock stack: a fixed sequence of 3-4 layers.",
            "- High terrain makes the same stack thicker instead of changing the order.",
            "- Layer borders drift slowly, so cuts should look like natural sloped strata.",
            "",
            "GT Earth surface and climate",
            "- GT Earth uses TFG-scale irregular regions, large continents, ocean shelves, blended shores and continuous river graphs.",
            "- Temperature and rainfall are continuous regional fields, not random biome patches.",
            "- Existing vanilla biome visuals are selected from terrain, temperature and rainfall; they do not control Earth height.",
            "- Dry, semi-arid, wet and freezing climates build different surface skins. Cold water freezes and cold land receives snow.",
            "- Lakes are attached to suitable river sources instead of being scattered as unrelated circular ponds.",
            "- Hotspots form active-to-ancient volcanic chains. Their terrain is part of the regional map.",
            "- Karst requires a limestone, dolomite, chalk or marble surface layer. Rainfall then selects tower, doline or cenote-like relief.",
            "- The temporal climate has 12 months, opposite hemispheric seasons, daily temperature changes and local precipitation probability.",
            "- Loaded cold terrain accumulates snow and ice during precipitation and thaws above freezing.",
            "- Use /gt6uou earth_climate in GT Earth to inspect current and average climate.",
            "",
            "GT Earth underground",
            "- GT Earth uses its own noise and noodle caves instead of inherited vanilla biome carvers.",
            "- Large cave, deep-cave and canyon shapes share the same groundwater system.",
            "- Aquifers can fill cavities with groundwater or deep lava; underwater caves should not become dry ocean cuts.",
            "- Cave floors keep their host rock by default. Wet and dry climates add local mud, moss or gravel patches; wet karst can form dripstone.",
            "- Wet karst and mountain terrain can form climate-specific underground river channels.",
            "",
            "Ores",
            "- Ore veins are tied to rock types, not only to Y levels.",
            "- A vein center must start inside one of its allowed host rocks.",
            "- The center needs about 20 blocks of host rock margin horizontally and vertically.",
            "- Once spawned, the vein can replace any allowed host rock it reaches.",
            "- Other rocks naturally cut the vein instead of being overwritten.",
            "- Each vein can generate as small, medium or large. Current chances: 20%, 60%, 20%.",
            "- Vein density depends on shape: layered 60%, mixed 70%, dike 75%, diamond 50%.",
            "- Rich ore is biased toward the center of a vein.",
            "- Poor ore is biased toward the outer edge of a vein.",
            "- Some ores drop crystals instead of raw material.",
            "- When a vein generates, 3-5 raw material blocks can appear on the natural surface or seafloor above it as temporary prospecting markers.",
            "- Raw oil can generate as large underground fluid pockets.",
            "- Bedrock fluid deposits are abstract chunk resources for future drills, not placed fluid blocks.",
            "",
            "How to prospect",
            "1. Use the mineral scanner to inspect nearby chunks.",
            "2. Switch the scanner to stone mode to identify geological regions.",
            "3. Switch to bedrock fluid mode to inspect abstract fluid deposits.",
            "4. Surface or seafloor raw blocks hint that a normal ore vein generated below.",
            "5. Find the rock type that can host the vein you need.",
            "6. Cut test sections with dev tools while balancing generation.",
            "",
            "This guide is intentionally small for now. More sections will be added as worldgen becomes stable."
    );

    private int left;
    private int top;
    private int scrollOffset;
    private int contentHeight;
    private final Screen parent;

    public GeologyHandbookScreen(Screen parent) {
        super(Component.literal("Geology handbook"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;

        InventoryInfoTab.createTabs(left + PANEL_WIDTH, top + 8, parent, InventoryInfoTab.Tab.HANDBOOK)
                .forEach(this::addRenderableWidget);
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 58, top + PANEL_HEIGHT - 24, 48, 18)
                .build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_COLOR);
        graphics.fill(left, top, left + 92, top + PANEL_HEIGHT, SIDE_COLOR);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, BORDER_COLOR);
        graphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, BORDER_COLOR);
        graphics.fill(left, top, left + 1, top + PANEL_HEIGHT, BORDER_COLOR);
        graphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BORDER_COLOR);

        graphics.drawString(font, "Geology Handbook", left + 10, top + 9, ACCENT_COLOR, false);
        drawSectionList(graphics);
        drawWorldGenerationPage(graphics);
        drawScrollbar(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= left + 96 && mouseX <= left + PANEL_WIDTH - 8
                && mouseY >= contentTop() && mouseY <= contentBottom()
                && maxScroll() > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * 18), 0, maxScroll());
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawSectionList(GuiGraphics graphics) {
        int sectionTop = top + 32;
        graphics.fill(left + 7, sectionTop - 4, left + 86, sectionTop + 15, SECTION_ACTIVE_COLOR);
        graphics.drawString(font, "World Generation", left + 11, sectionTop + 1, TEXT_COLOR, false);
    }

    private void drawWorldGenerationPage(GuiGraphics graphics) {
        int textX = left + 104;
        int y = contentTop() - scrollOffset;
        int maxWidth = contentWidth();
        int bottom = contentBottom();

        graphics.enableScissor(textX - 2, contentTop(), left + PANEL_WIDTH - 12, bottom);

        for (String line : WORLDGEN_TEXT) {
            if (line.isEmpty()) {
                y += 6;
                continue;
            }

            int color = line.equals("World Generation") || line.equals("Rock layers") || line.equals("GT Earth surface and climate") || line.equals("GT Earth underground") || line.equals("Ores") || line.equals("How to prospect")
                    ? ACCENT_COLOR
                    : line.startsWith("-") || Character.isDigit(line.charAt(0)) ? MUTED_TEXT_COLOR : TEXT_COLOR;

            for (String wrapped : wrapLine(line, maxWidth)) {
                if (y >= contentTop() - 10 && y <= bottom) {
                    graphics.drawString(font, wrapped, textX, y, color, false);
                }
                y += 10;
            }
        }

        graphics.disableScissor();
        contentHeight = y - (contentTop() - scrollOffset);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int maxScroll = maxScroll();

        if (maxScroll <= 0) {
            return;
        }

        int trackLeft = left + PANEL_WIDTH - 9;
        int trackTop = contentTop();
        int trackBottom = contentBottom();
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
        int thumbTop = trackTop + scrollOffset * (trackHeight - thumbHeight) / maxScroll;

        graphics.fill(trackLeft, trackTop, trackLeft + 4, trackBottom, SCROLLBAR_TRACK_COLOR);
        graphics.fill(trackLeft, thumbTop, trackLeft + 4, thumbTop + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    private int contentTop() {
        return top + 12;
    }

    private int contentBottom() {
        return top + PANEL_HEIGHT - 31;
    }

    private int contentWidth() {
        return PANEL_WIDTH - 126;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (contentBottom() - contentTop()));
    }

    private List<String> wrapLine(String line, int maxWidth) {
        if (font.width(line) <= maxWidth) {
            return List.of(line);
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : line.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;

            if (font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }

            current.append(word);
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines;
    }
}
