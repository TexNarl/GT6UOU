package gregtech.client.earth;

import gregapi.data.DimensionList;
import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.climate.EarthSeasonalClimate;
import gregtech.worldgen.earth.GTEarthChunkGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/** Calendar and climate pages opened by the inventory-side tabs. */
public final class EarthInfoScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 176;
    private static final int PANEL_COLOR = 0xEEA8A8A8;
    private static final int BORDER_DARK = 0xFF353535;
    private static final int BORDER_LIGHT = 0xFFE5E5E5;
    private static final int TEXT_COLOR = 0xFF292929;
    private static final int MUTED_COLOR = 0xFF555555;

    private final Screen parent;
    private final Mode mode;
    private int left;
    private int top;

    public EarthInfoScreen(Screen parent, Mode mode) {
        super(Component.literal(mode.title));
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        InventoryInfoTab.Tab active = mode == Mode.CALENDAR
                ? InventoryInfoTab.Tab.CALENDAR
                : InventoryInfoTab.Tab.CLIMATE;
        InventoryInfoTab.createTabs(left + PANEL_WIDTH, top + 8, parent, active)
                .forEach(this::addRenderableWidget);
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 58, top + PANEL_HEIGHT - 25, 48, 18)
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
        graphics.fill(0, 0, width, height, 0x66000000);
        drawPanel(graphics);
        graphics.drawString(font, mode.title, left + 12, top + 11, TEXT_COLOR, false);
        if (mode == Mode.CALENDAR) {
            drawCalendar(graphics);
        } else {
            drawClimate(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics graphics) {
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BORDER_DARK);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, BORDER_LIGHT);
        graphics.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3, PANEL_COLOR);
    }

    private void drawCalendar(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            drawUnavailable(graphics, "No world is loaded");
            return;
        }

        long gameTime = client.level.getGameTime();
        long dayTime = Math.floorMod(client.level.getDayTime(), EarthSeasonalClimate.TICKS_IN_DAY);
        int hour = (int) ((dayTime / 1000L + 6L) % 24L);
        int minute = (int) ((dayTime % 1000L) * 60L / 1000L);
        EarthSeasonalClimate.CalendarSample calendar = calendarSample(gameTime);

        int y = top + 42;
        if (calendar != null) {
            drawCentered(graphics, "Season: " + calendar.season(), y);
            drawCentered(graphics, "Day: " + calendar.weekday(), y + 16);
            drawCentered(graphics, String.format(Locale.ROOT, "Date: %02d:%02d, %s %d, %d",
                    hour, minute, calendar.month(), calendar.dayOfMonth(), calendar.year()), y + 32);
        } else {
            drawCentered(graphics, String.format(Locale.ROOT, "Time: %02d:%02d", hour, minute), y);
            drawCentered(graphics, "Seasonal calendar is available in GT Earth", y + 20);
        }
    }

    private void drawClimate(GuiGraphics graphics) {
        ClimateView climate = climateView();
        if (climate == null) {
            drawUnavailable(graphics, "Climate data is available in singleplayer GT Earth");
            return;
        }

        int y = top + 37;
        drawCentered(graphics, "Climate: " + climate.classification, y);
        drawCentered(graphics, String.format(Locale.ROOT, "Avg. Temp: %.1f°C", climate.averageTemperature), y + 16);
        drawCentered(graphics, String.format(Locale.ROOT, "Annual Rainfall: %.1f mm", climate.annualRainfall), y + 32);
        drawCentered(graphics, String.format(Locale.ROOT, "Current Temp: %.1f°C", climate.currentTemperature), y + 48);
        drawCentered(graphics, "Season: " + climate.season, y + 64);
        drawCentered(graphics, climate.precipitating
                ? String.format(Locale.ROOT, "Precipitation: %.0f%%", climate.precipitationIntensity * 100.0F)
                : "Precipitation: none", y + 80);
    }

    private void drawUnavailable(GuiGraphics graphics, String message) {
        drawCentered(graphics, message, top + 74);
    }

    private void drawCentered(GuiGraphics graphics, String text, int y) {
        int x = left + (PANEL_WIDTH - font.width(text)) / 2;
        graphics.drawString(font, text, x, y, TEXT_COLOR, false);
    }

    private EarthSeasonalClimate.CalendarSample calendarSample(long gameTime) {
        EarthGeneratorState state = earthState();
        return state == null ? null : state.seasonalClimate().calendar(gameTime);
    }

    private ClimateView climateView() {
        Minecraft client = Minecraft.getInstance();
        EarthGeneratorState state = earthState();
        if (client.level == null || client.player == null || state == null) {
            return null;
        }

        BlockPos pos = client.player.blockPosition();
        long gameTime = client.level.getGameTime();
        var baseline = state.climateModel().sample(pos.getX(), pos.getZ());
        var current = state.seasonalClimate().sample(pos.getX(), pos.getY(), pos.getZ(), gameTime);
        return new ClimateView(
                classify(baseline.averageTemperature(), baseline.averageRainfall()),
                baseline.averageTemperature(),
                baseline.averageRainfall(),
                current.temperature(),
                current.season(),
                current.precipitating(),
                current.precipitationIntensity()
        );
    }

    private EarthGeneratorState earthState() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null
                || !client.level.dimension().equals(DimensionList.EARTH)
                || client.getSingleplayerServer() == null) {
            return null;
        }

        ServerLevel earth = client.getSingleplayerServer().getLevel(DimensionList.EARTH);
        if (earth == null || !(earth.getChunkSource().getGenerator() instanceof GTEarthChunkGenerator generator)) {
            return null;
        }
        return generator.earthState();
    }

    private static String classify(float temperature, float rainfall) {
        String thermal = temperature < 0.0F ? "Polar"
                : temperature < 8.0F ? "Cold"
                : temperature < 18.0F ? "Temperate"
                : temperature < 27.0F ? "Warm"
                : "Tropical";
        String moisture = rainfall < 80.0F ? "arid"
                : rainfall < 180.0F ? "dry"
                : rainfall < 330.0F ? "humid"
                : "rainforest";
        return thermal + " " + moisture;
    }

    public enum Mode {
        CALENDAR("Calendar"),
        CLIMATE("Climate");

        private final String title;

        Mode(String title) {
            this.title = title;
        }
    }

    private record ClimateView(
            String classification,
            float averageTemperature,
            float annualRainfall,
            float currentTemperature,
            String season,
            boolean precipitating,
            float precipitationIntensity
    ) {
    }
}
