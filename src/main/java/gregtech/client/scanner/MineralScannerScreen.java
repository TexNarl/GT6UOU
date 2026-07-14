package gregtech.client.scanner;

import com.mojang.blaze3d.platform.NativeImage;
import gregapi.data.BedrockFluidList;
import gregapi.data.BedrockOreList;
import gregapi.worldgen.GeologicalRules.RegionType;

import gregapi.data.RocksList;

import gregapi.code.BlockTypeDefinitions;

import gregapi.code.MineralResource;
import gregapi.code.OreBlockForm;
import gregapi.data.NaturalResourceList;
import gregapi.tools.ScannerList;
import gregapi.worldgen.BedrockFluidRules.BedrockFluidDefinition;
import gregapi.worldgen.BedrockOreRules.BedrockOreDefinition;
import gregapi.worldgen.BedrockOreRules.WeightedOreResource;
import gregtech.GT6UOU;
import gregtech.worldgen.resources.BedrockFluidWorldData;
import gregtech.worldgen.resources.BedrockOreWorldData;
import gregapi.data.DimensionList;
import gregtech.worldgen.earth.GTEarthChunkGenerator;
import gregtech.worldgen.geology.StoneLayerStackSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MineralScannerScreen extends Screen {
    private static final int CHUNK_GRID_SIZE = 13;
    private static final int CHUNK_SIZE = 16;
    private static final int MAP_SIZE = CHUNK_GRID_SIZE * CHUNK_SIZE;
    private static final int PANEL_WIDTH = MAP_SIZE + 160;
    private static final int PANEL_HEIGHT = MAP_SIZE + 34;
    private static final int LIGHT_PANEL_COLOR = 0xFF202020;
    private static final int DARK_PANEL_COLOR = 0xFF111214;
    private static final int LIGHT_MAP_EMPTY_COLOR = 0xFFFFFFFF;
    private static final int DARK_MAP_EMPTY_COLOR = 0xFF18191C;
    private static final int LIGHT_GRID_COLOR = 0xDD000000;
    private static final int DARK_GRID_COLOR = 0xFF3A3D42;
    private static final int LIGHT_TEXT_COLOR = 0xFFE0E0E0;
    private static final int DARK_TEXT_COLOR = 0xFFEDEFF2;
    private static final int LIGHT_MUTED_TEXT_COLOR = 0xFF909090;
    private static final int DARK_MUTED_TEXT_COLOR = 0xFFA8ADB7;
    private static final int PLAYER_CROSS_COLOR = 0xFFFF0000;
    private static final int PLAYER_ARROW_COLOR = 0xFFFF2020;
    private static final int PLAYER_ARROW_OUTLINE_COLOR = 0xFF000000;
    private static final int SCAN_COLUMNS_PER_TICK = 576;
    private static final Set<String> ROCK_BLOCK_IDS = RocksList.ROCKS.stream()
            .map(BlockTypeDefinitions.RockType::id)
            .collect(Collectors.toUnmodifiableSet());
    private static boolean nightMode = true;
    private static DisplayMode savedDisplayMode = DisplayMode.MINERALS;

    private ScanResult scanResult = ScanResult.empty();
    private ScanCacheEntry activeScan;
    private DisplayMode displayMode = savedDisplayMode;
    private String selectedResourceId;
    private int startChunkX;
    private int startChunkZ;
    private int left;
    private int top;
    private int listScrollOffset;
    private boolean mineralMapDirty = true;
    private DynamicTexture mineralMapTexture;
    private ResourceLocation mineralMapTextureId;
    private StoneLayerStackSampler cachedStoneLayerSampler;
    private final Map<Long, List<String>> actualRockStackCache = new HashMap<>();
    private final Map<String, Integer> bedrockFluidTotals = new LinkedHashMap<>();
    private final Map<String, Integer> bedrockOreTotals = new LinkedHashMap<>();

    public MineralScannerScreen() {
        super(Component.literal("Mineral scanner"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2 + 8;
        startScan();
        rebuildButtons();
    }

    @Override
    public void tick() {
        if (activeScan != null && !activeScan.scanningComplete) {
            scanNextColumns();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    private void rebuildButtons() {
        clearWidgets();

        int listX = left + MAP_SIZE + 18;
        int y = top + 25;
        addRenderableWidget(Button.builder(
                        Component.literal("Mode: " + displayMode.title()),
                        button -> {
                            displayMode = displayMode.next();
                            savedDisplayMode = displayMode;
                            listScrollOffset = 0;
                            rebuildButtons();
                        })
                .bounds(listX, y, 120, 18)
                .build());
        y += 22;

        addRenderableWidget(Button.builder(
                        Component.literal(nightMode ? "Night mode: on" : "Night mode: off"),
                        button -> {
                            nightMode = !nightMode;
                            rebuildButtons();
                        })
                .bounds(listX, y, 120, 18)
                .build());
        y += 22;

        if (displayMode == DisplayMode.STONE_CATEGORIES) {
            addRenderableWidget(Button.builder(Component.literal("C: Continental"), button -> {})
                    .bounds(listX, y, 120, 18)
                    .build());
            y += 20;
            addRenderableWidget(Button.builder(Component.literal("V: Volcanic"), button -> {})
                    .bounds(listX, y, 120, 18)
                    .build());
            y += 20;
            addRenderableWidget(Button.builder(Component.literal("U: Uplift"), button -> {})
                    .bounds(listX, y, 120, 18)
                    .build());
            y += 20;
            addRenderableWidget(Button.builder(Component.literal("M: Mafic deep"), button -> {})
                    .bounds(listX, y, 120, 18)
                    .build());
            return;
        }

        if (displayMode == DisplayMode.BEDROCK_FLUIDS) {
            addRenderableWidget(Button.builder(
                            Component.literal(selectedResourceId == null ? "All fluids" : "Show all"),
                            button -> {
                                selectedResourceId = null;
                                rebuildButtons();
                            })
                    .bounds(listX, y, 120, 18)
                    .build());
            y += 22;

            List<Map.Entry<String, Integer>> entries = List.copyOf(bedrockFluidTotals.entrySet());
            int visibleRows = visibleListRows(y);
            listScrollOffset = clamp(listScrollOffset, 0, Math.max(0, entries.size() - visibleRows));

            for (Map.Entry<String, Integer> entry : entries.subList(listScrollOffset, Math.min(entries.size(), listScrollOffset + visibleRows))) {
                BedrockFluidDefinition definition = BedrockFluidList.byId(entry.getKey());
                String name = definition == null ? entry.getKey() : definition.englishName();
                Component label = Component.literal(name + " (" + entry.getValue() + ")");
                addRenderableWidget(Button.builder(label, button -> {
                            selectedResourceId = entry.getKey().equals(selectedResourceId) ? null : entry.getKey();
                            rebuildButtons();
                        })
                        .bounds(listX, y, 120, 18)
                        .build());
                y += 20;
            }
            return;
        }

        if (displayMode == DisplayMode.BEDROCK_ORES) {
            addRenderableWidget(Button.builder(
                            Component.literal(selectedResourceId == null ? "All bedrock ores" : "Show all"),
                            button -> {
                                selectedResourceId = null;
                                rebuildButtons();
                            })
                    .bounds(listX, y, 120, 18)
                    .build());
            y += 22;

            List<Map.Entry<String, Integer>> entries = List.copyOf(bedrockOreTotals.entrySet());
            int visibleRows = visibleListRows(y);
            listScrollOffset = clamp(listScrollOffset, 0, Math.max(0, entries.size() - visibleRows));

            for (Map.Entry<String, Integer> entry : entries.subList(listScrollOffset, Math.min(entries.size(), listScrollOffset + visibleRows))) {
                BedrockOreDefinition definition = BedrockOreList.byId(entry.getKey());
                String name = definition == null ? entry.getKey() : definition.englishName();
                Component label = Component.literal(name + " (" + entry.getValue() + ")");
                addRenderableWidget(Button.builder(label, button -> {
                            selectedResourceId = entry.getKey().equals(selectedResourceId) ? null : entry.getKey();
                            rebuildButtons();
                        })
                        .bounds(listX, y, 120, 18)
                        .build());
                y += 20;
            }
            return;
        }

        addRenderableWidget(Button.builder(
                        Component.literal(selectedResourceId == null ? "All resources" : "Show all"),
                        button -> {
                            selectedResourceId = null;
                            mineralMapDirty = true;
                            rebuildButtons();
                        })
                .bounds(listX, y, 120, 18)
                .build());
        y += 22;

        List<MineralResource> resources = scanResult.resources();
        int visibleRows = visibleListRows(y);
        listScrollOffset = clamp(listScrollOffset, 0, Math.max(0, resources.size() - visibleRows));

        for (MineralResource resource : resources.subList(listScrollOffset, Math.min(resources.size(), listScrollOffset + visibleRows))) {
            String id = resource.id();
            Component label = Component.literal(resource.englishName() + " (" + scanResult.totalCount(id) + ")");
            addRenderableWidget(Button.builder(label, button -> {
                        selectedResourceId = id.equals(selectedResourceId) ? null : id;
                        mineralMapDirty = true;
                        rebuildButtons();
                    })
                    .bounds(listX, y, 120, 18)
                    .build());
            y += 20;
        }
    }

    private int visibleListRows(int firstRowY) {
        int bottom = top + PANEL_HEIGHT - 6;
        return Math.max(0, (bottom - firstRowY) / 20);
    }

    private int currentMaxListScroll() {
        int visibleRows = visibleListRows(listFirstRowY());
        int listSize = switch (displayMode) {
            case MINERALS -> scanResult.resources().size();
            case BEDROCK_FLUIDS -> bedrockFluidTotals.size();
            case BEDROCK_ORES -> bedrockOreTotals.size();
            case STONE_CATEGORIES -> 0;
        };
        return Math.max(0, listSize - visibleRows);
    }

    private int listFirstRowY() {
        return top + 25 + 22 + 22 + 22;
    }

    private boolean isMouseOverResourceList(double mouseX, double mouseY) {
        int listX = left + MAP_SIZE + 18;
        return mouseX >= listX
                && mouseX <= left + PANEL_WIDTH
                && mouseY >= top + 25
                && mouseY <= top + PANEL_HEIGHT;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = currentMaxListScroll();

        if (maxScroll > 0 && isMouseOverResourceList(mouseX, mouseY)) {
            int direction = scrollY > 0.0D ? -1 : 1;
            listScrollOffset = clamp(listScrollOffset + direction, 0, maxScroll);
            rebuildButtons();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, nightMode ? 0x99000000 : 0x66000000);
        graphics.fill(left - 8, top - 18, left + PANEL_WIDTH, top + PANEL_HEIGHT, panelColor());
        graphics.drawString(font, title, left, top - 12, textColor(), false);
        int progress = activeScan == null ? 0 : activeScan.nextColumnIndex;
        boolean complete = activeScan == null || activeScan.scanningComplete;
        String scanText = complete
                ? CHUNK_GRID_SIZE + "x" + CHUNK_GRID_SIZE + " chunks, scan complete"
                : CHUNK_GRID_SIZE + "x" + CHUNK_GRID_SIZE + " chunks, scanning " + progress + "/" + (MAP_SIZE * MAP_SIZE);
        graphics.drawString(font, scanText, left, top + MAP_SIZE + 6, mutedTextColor(), false);
        graphics.drawString(font, displayMode == DisplayMode.STONE_CATEGORIES ? "Regions" : "Detected", left + MAP_SIZE + 18, top + 10, textColor(), false);

        drawMap(graphics);
        drawPlayerCross(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawListScrollbar(graphics);
        drawHoverTooltip(graphics, mouseX, mouseY);
    }

    private void drawListScrollbar(GuiGraphics graphics) {
        int maxScroll = currentMaxListScroll();

        if (maxScroll <= 0) {
            return;
        }

        int listSize = switch (displayMode) {
            case MINERALS -> scanResult.resources().size();
            case BEDROCK_FLUIDS -> bedrockFluidTotals.size();
            case BEDROCK_ORES -> bedrockOreTotals.size();
            case STONE_CATEGORIES -> 0;
        };

        if (listSize <= 0) {
            return;
        }

        int trackX = left + MAP_SIZE + 142;
        int trackTop = listFirstRowY();
        int trackBottom = top + PANEL_HEIGHT - 6;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int visibleRows = visibleListRows(trackTop);
        int thumbHeight = Math.max(12, trackHeight * visibleRows / listSize);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbTop = trackTop + travel * listScrollOffset / maxScroll;

        graphics.fill(trackX, trackTop, trackX + 4, trackBottom, nightMode ? 0xFF2A2D33 : 0xFF404040);
        graphics.fill(trackX, thumbTop, trackX + 4, thumbTop + thumbHeight, nightMode ? 0xFFEDEFF2 : 0xFFE0E0E0);
    }

    private void drawMap(GuiGraphics graphics) {
        graphics.fill(left, top, left + MAP_SIZE, top + MAP_SIZE, mapEmptyColor());

        if (displayMode == DisplayMode.STONE_CATEGORIES) {
            drawStoneCategoryMap(graphics);
        } else if (displayMode == DisplayMode.BEDROCK_FLUIDS) {
            drawBedrockFluidMap(graphics);
        } else if (displayMode == DisplayMode.BEDROCK_ORES) {
            drawBedrockOreMap(graphics);
        } else {
            drawMineralMap(graphics);
        }

        for (int i = 0; i <= CHUNK_GRID_SIZE; i++) {
            int offset = i * CHUNK_SIZE;
            graphics.fill(left + offset, top, left + offset + 1, top + MAP_SIZE, gridColor());
            graphics.fill(left, top + offset, left + MAP_SIZE, top + offset + 1, gridColor());
        }
    }

    private void drawMineralMap(GuiGraphics graphics) {
        if (mineralMapDirty) {
            rebuildMineralMapTexture();
        }

        if (mineralMapTextureId != null) {
            graphics.blit(mineralMapTextureId, left, top, 0.0F, 0.0F, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);
        }
    }

    private void rebuildMineralMapTexture() {
        ensureMineralMapTexture();

        for (int z = 0; z < MAP_SIZE; z++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                mineralMapTexture.getPixels().setPixelRGBA(x, z, nativeImageColor(visibleResourceColorAt(x, z)));
            }
        }

        mineralMapTexture.upload();
        mineralMapDirty = false;
    }

    private void ensureMineralMapTexture() {
        if (mineralMapTexture != null && mineralMapTextureId != null) {
            return;
        }

        mineralMapTexture = new DynamicTexture(MAP_SIZE, MAP_SIZE, true);
        mineralMapTextureId = Minecraft.getInstance().getTextureManager().register("gt6uou_mineral_scanner_map", mineralMapTexture);
    }

    private static int nativeImageColor(int argb) {
        if (argb == 0) {
            return 0;
        }

        int alpha = argb & 0xFF000000;
        int red = (argb >> 16) & 0xFF;
        int green = argb & 0x0000FF00;
        int blue = (argb & 0xFF) << 16;

        return alpha | blue | green | red;
    }

    private void drawStoneCategoryMap(GuiGraphics graphics) {
        StoneLayerStackSampler sampler = stoneLayerSampler();

        if (sampler == null) {
            return;
        }

        for (int chunkGridX = 0; chunkGridX < CHUNK_GRID_SIZE; chunkGridX++) {
            for (int chunkGridZ = 0; chunkGridZ < CHUNK_GRID_SIZE; chunkGridZ++) {
                int chunkX = startChunkX + chunkGridX;
                int chunkZ = startChunkZ + chunkGridZ;
                int blockX = chunkX * CHUNK_SIZE + CHUNK_SIZE / 2;
                int blockZ = chunkZ * CHUNK_SIZE + CHUNK_SIZE / 2;
                StoneLayerStackSampler.StoneLayerStackSample sample = sampler.sampleStack(blockX, blockZ);
                int cellLeft = left + chunkGridX * CHUNK_SIZE;
                int cellTop = top + chunkGridZ * CHUNK_SIZE;
                graphics.fill(cellLeft + 1, cellTop + 1, cellLeft + CHUNK_SIZE, cellTop + CHUNK_SIZE, regionColor(sample.regionType()));

                String letter = regionLetter(sample.regionType());
                int letterX = cellLeft + (CHUNK_SIZE - font.width(letter)) / 2 + 1;
                int letterY = cellTop + 4;
                graphics.drawString(font, letter, letterX + 1, letterY + 1, 0xCC000000, false);
                graphics.drawString(font, letter, letterX, letterY, 0xFFFFFFFF, false);
            }
        }
    }

    private void drawBedrockFluidMap(GuiGraphics graphics) {
        for (int chunkGridX = 0; chunkGridX < CHUNK_GRID_SIZE; chunkGridX++) {
            for (int chunkGridZ = 0; chunkGridZ < CHUNK_GRID_SIZE; chunkGridZ++) {
                int chunkX = startChunkX + chunkGridX;
                int chunkZ = startChunkZ + chunkGridZ;
                BedrockFluidWorldData.BedrockFluidEntry entry = bedrockFluidEntry(chunkX, chunkZ);

                if (entry == null || entry.definitionId() == null
                        || selectedResourceId != null && !selectedResourceId.equals(entry.definitionId())) {
                    continue;
                }

                int cellLeft = left + chunkGridX * CHUNK_SIZE;
                int cellTop = top + chunkGridZ * CHUNK_SIZE;
                graphics.fill(cellLeft + 1, cellTop + 1, cellLeft + CHUNK_SIZE, cellTop + CHUNK_SIZE,
                        bedrockFluidColor(entry.definitionId()));
            }
        }
    }

    private void drawBedrockOreMap(GuiGraphics graphics) {
        for (int chunkGridX = 0; chunkGridX < CHUNK_GRID_SIZE; chunkGridX++) {
            for (int chunkGridZ = 0; chunkGridZ < CHUNK_GRID_SIZE; chunkGridZ++) {
                int chunkX = startChunkX + chunkGridX;
                int chunkZ = startChunkZ + chunkGridZ;
                BedrockOreWorldData.BedrockOreEntry entry = bedrockOreEntry(chunkX, chunkZ);

                if (entry == null || entry.definitionId() == null
                        || selectedResourceId != null && !selectedResourceId.equals(entry.definitionId())) {
                    continue;
                }

                int cellLeft = left + chunkGridX * CHUNK_SIZE;
                int cellTop = top + chunkGridZ * CHUNK_SIZE;
                graphics.fill(cellLeft + 1, cellTop + 1, cellLeft + CHUNK_SIZE, cellTop + CHUNK_SIZE,
                        bedrockOreColor(entry.definitionId()));
            }
        }
    }

    private static int panelColor() {
        return nightMode ? DARK_PANEL_COLOR : LIGHT_PANEL_COLOR;
    }

    private static int mapEmptyColor() {
        return nightMode ? DARK_MAP_EMPTY_COLOR : LIGHT_MAP_EMPTY_COLOR;
    }

    private static int gridColor() {
        return nightMode ? DARK_GRID_COLOR : LIGHT_GRID_COLOR;
    }

    private static int textColor() {
        return nightMode ? DARK_TEXT_COLOR : LIGHT_TEXT_COLOR;
    }

    private static int mutedTextColor() {
        return nightMode ? DARK_MUTED_TEXT_COLOR : LIGHT_MUTED_TEXT_COLOR;
    }

    private int visibleResourceColorAt(int x, int z) {
        String resourceId = scanResult.highestResourceAt(x, z);

        if (resourceId == null || selectedResourceId != null && !selectedResourceId.equals(resourceId)) {
            return 0;
        }

        return colorFor(resourceId);
    }

    private void drawPlayerCross(GuiGraphics graphics) {
        Player player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        int mapX = player.getBlockX() - startChunkX * CHUNK_SIZE;
        int mapZ = player.getBlockZ() - startChunkZ * CHUNK_SIZE;

        if (mapX < 0 || mapZ < 0 || mapX >= MAP_SIZE || mapZ >= MAP_SIZE) {
            return;
        }

        graphics.fill(left + mapX, top, left + mapX + 1, top + MAP_SIZE, PLAYER_CROSS_COLOR);
        graphics.fill(left, top + mapZ, left + MAP_SIZE, top + mapZ + 1, PLAYER_CROSS_COLOR);
        drawPlayerDirectionArrow(graphics, player, mapX, mapZ);
    }

    private void drawPlayerDirectionArrow(GuiGraphics graphics, Player player, int mapX, int mapZ) {
        double yawRadians = Math.toRadians(player.getYRot());
        int centerX = left + mapX;
        int centerZ = top + mapZ;
        double directionX = -Math.sin(yawRadians);
        double directionZ = Math.cos(yawRadians);
        double perpendicularX = -directionZ;
        double perpendicularZ = directionX;

        drawPlayerArrowTriangle(graphics, centerX, centerZ, directionX, directionZ, perpendicularX, perpendicularZ,
                14.0D, 8.0D, 7.0D, PLAYER_ARROW_OUTLINE_COLOR);
        drawPlayerArrowTriangle(graphics, centerX, centerZ, directionX, directionZ, perpendicularX, perpendicularZ,
                12.0D, 6.0D, 5.0D, PLAYER_ARROW_COLOR);
    }

    private static void drawPlayerArrowTriangle(
            GuiGraphics graphics,
            int centerX,
            int centerZ,
            double directionX,
            double directionZ,
            double perpendicularX,
            double perpendicularZ,
            double tipDistance,
            double tailDistance,
            double halfWidth,
            int color
    ) {
        int tipX = centerX + (int) Math.round(directionX * tipDistance);
        int tipZ = centerZ + (int) Math.round(directionZ * tipDistance);
        int leftX = centerX + (int) Math.round((-directionX * tailDistance) + (perpendicularX * halfWidth));
        int leftZ = centerZ + (int) Math.round((-directionZ * tailDistance) + (perpendicularZ * halfWidth));
        int rightX = centerX + (int) Math.round((-directionX * tailDistance) - (perpendicularX * halfWidth));
        int rightZ = centerZ + (int) Math.round((-directionZ * tailDistance) - (perpendicularZ * halfWidth));

        fillTriangle(graphics, tipX, tipZ, leftX, leftZ, rightX, rightZ, color);
    }

    private static void fillTriangle(
            GuiGraphics graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int x2,
            int y2,
            int color
    ) {
        int minX = Math.min(x0, Math.min(x1, x2));
        int maxX = Math.max(x0, Math.max(x1, x2));
        int minY = Math.min(y0, Math.min(y1, y2));
        int maxY = Math.max(y0, Math.max(y1, y2));

        for (int y = minY; y <= maxY; y++) {
            int runStartX = Integer.MIN_VALUE;

            for (int x = minX; x <= maxX; x++) {
                if (isInsideTriangle(x, y, x0, y0, x1, y1, x2, y2)) {
                    if (runStartX == Integer.MIN_VALUE) {
                        runStartX = x;
                    }
                    continue;
                }

                if (runStartX != Integer.MIN_VALUE) {
                    graphics.fill(runStartX, y, x, y + 1, color);
                    runStartX = Integer.MIN_VALUE;
                }
            }

            if (runStartX != Integer.MIN_VALUE) {
                graphics.fill(runStartX, y, maxX + 1, y + 1, color);
            }
        }
    }

    private static boolean isInsideTriangle(int x, int y, int x0, int y0, int x1, int y1, int x2, int y2) {
        int d0 = edgeFunction(x, y, x0, y0, x1, y1);
        int d1 = edgeFunction(x, y, x1, y1, x2, y2);
        int d2 = edgeFunction(x, y, x2, y2, x0, y0);
        boolean hasNegative = d0 < 0 || d1 < 0 || d2 < 0;
        boolean hasPositive = d0 > 0 || d1 > 0 || d2 > 0;

        return !(hasNegative && hasPositive);
    }

    private static int edgeFunction(int x, int y, int ax, int ay, int bx, int by) {
        return (x - ax) * (by - ay) - (y - ay) * (bx - ax);
    }

    private void drawHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int localX = mouseX - left;
        int localZ = mouseY - top;

        if (localX < 0 || localZ < 0 || localX >= MAP_SIZE || localZ >= MAP_SIZE) {
            return;
        }

        int chunkGridX = localX / CHUNK_SIZE;
        int chunkGridZ = localZ / CHUNK_SIZE;

        if (displayMode == DisplayMode.STONE_CATEGORIES) {
            drawStoneCategoryTooltip(graphics, mouseX, mouseY, chunkGridX, chunkGridZ);
            return;
        }

        if (displayMode == DisplayMode.BEDROCK_FLUIDS) {
            drawBedrockFluidTooltip(graphics, mouseX, mouseY, chunkGridX, chunkGridZ);
            return;
        }

        if (displayMode == DisplayMode.BEDROCK_ORES) {
            drawBedrockOreTooltip(graphics, mouseX, mouseY, chunkGridX, chunkGridZ);
            return;
        }

        ChunkScan chunk = scanResult.chunk(chunkGridX, chunkGridZ);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Chunk " + chunk.chunkX() + ", " + chunk.chunkZ()));

        if (chunk.counts().isEmpty()) {
            lines.add(Component.literal("No useful minerals"));
        } else {
            chunk.counts().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        MineralResource resource = NaturalResourceList.byId(entry.getKey()).orElse(null);
                        String name = resource == null ? entry.getKey() : resource.englishName();
                        lines.add(Component.literal(name + ": " + entry.getValue()));
                    });
        }

        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void drawBedrockFluidTooltip(GuiGraphics graphics, int mouseX, int mouseY, int chunkGridX, int chunkGridZ) {
        int chunkX = startChunkX + chunkGridX;
        int chunkZ = startChunkZ + chunkGridZ;
        BedrockFluidWorldData.BedrockFluidEntry entry = bedrockFluidEntry(chunkX, chunkZ);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Chunk " + chunkX + ", " + chunkZ));

        if (entry == null || entry.definitionId() == null || entry.definition() == null) {
            lines.add(Component.literal("No bedrock fluid"));
        } else {
            BedrockFluidDefinition definition = entry.definition();
            lines.add(Component.literal(definition.englishName()));
            lines.add(Component.literal("Yield: " + entry.currentYield() + " / " + entry.fluidYield()));
            lines.add(Component.literal("Remaining: " + Math.round(entry.remainingPercent()) + "%"));
            lines.add(Component.literal("Modifier: x" + String.format(java.util.Locale.ROOT, "%.3f", entry.yieldModifier())));
        }

        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void drawBedrockOreTooltip(GuiGraphics graphics, int mouseX, int mouseY, int chunkGridX, int chunkGridZ) {
        int chunkX = startChunkX + chunkGridX;
        int chunkZ = startChunkZ + chunkGridZ;
        BedrockOreWorldData.BedrockOreEntry entry = bedrockOreEntry(chunkX, chunkZ);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Chunk " + chunkX + ", " + chunkZ));

        if (entry == null || entry.definitionId() == null || entry.definition() == null) {
            lines.add(Component.literal("No bedrock ore"));
        } else {
            BedrockOreDefinition definition = entry.definition();
            lines.add(Component.literal(definition.englishName()));
            lines.add(Component.literal("Yield: " + entry.currentYield() + " / " + entry.oreYield()));
            lines.add(Component.literal("Remaining: " + Math.round(entry.remainingPercent()) + "%"));
            lines.add(Component.literal("Modifier: x" + String.format(java.util.Locale.ROOT, "%.3f", entry.yieldModifier())));

            if (!entry.ores().isEmpty()) {
                lines.add(Component.literal("Materials:"));
                for (WeightedOreResource ore : entry.ores()) {
                    MineralResource resource = NaturalResourceList.byId(ore.resourceId()).orElse(null);
                    String name = resource == null ? ore.resourceId() : resource.englishName();
                    lines.add(Component.literal("- " + name + " (" + ore.weight() + ")"));
                }
            }
        }

        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void drawStoneCategoryTooltip(GuiGraphics graphics, int mouseX, int mouseY, int chunkGridX, int chunkGridZ) {
        StoneLayerStackSampler sampler = stoneLayerSampler();

        if (sampler == null) {
            return;
        }

        int chunkX = startChunkX + chunkGridX;
        int chunkZ = startChunkZ + chunkGridZ;
        int blockX = chunkX * CHUNK_SIZE + CHUNK_SIZE / 2;
        int blockZ = chunkZ * CHUNK_SIZE + CHUNK_SIZE / 2;
        int surfaceY = surfaceY(blockX, blockZ);
        StoneLayerStackSampler.StoneLayerStackSample sample = sampler.sampleStack(blockX, blockZ);
        long cacheKey = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        List<String> actualRockIds = actualRockStackCache.computeIfAbsent(cacheKey, ignored -> actualRockStack(sampler, blockX, blockZ, surfaceY));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Chunk " + chunkX + ", " + chunkZ));
        lines.add(Component.literal("Region: " + readableRegionName(sample.regionType())));
        lines.add(Component.literal("Layers: " + actualRockIds.size() + " at surface Y " + surfaceY));
        lines.add(Component.literal("Stack: " + actualRockIds.stream()
                .map(MineralScannerScreen::readableRockName)
                .reduce((left, right) -> left + " -> " + right)
                .orElse("Unknown")));

        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void startScan() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null || minecraft.level == null) {
            this.scanResult = ScanResult.empty();
            this.activeScan = null;
            return;
        }

        ChunkPos center = player.chunkPosition();
        this.startChunkX = center.x - CHUNK_GRID_SIZE / 2;
        this.startChunkZ = center.z - CHUNK_GRID_SIZE / 2;

        String[][] highestResources = new String[MAP_SIZE][MAP_SIZE];
        ChunkScan[][] chunks = new ChunkScan[CHUNK_GRID_SIZE][CHUNK_GRID_SIZE];
        Map<String, Integer> totalCounts = new LinkedHashMap<>();

        for (int chunkGridX = 0; chunkGridX < CHUNK_GRID_SIZE; chunkGridX++) {
            for (int chunkGridZ = 0; chunkGridZ < CHUNK_GRID_SIZE; chunkGridZ++) {
                int chunkX = startChunkX + chunkGridX;
                int chunkZ = startChunkZ + chunkGridZ;
                chunks[chunkGridX][chunkGridZ] = new ChunkScan(
                        chunkX,
                        chunkZ,
                        new LinkedHashMap<>()
                );
            }
        }

        ScanResult createdResult = new ScanResult(highestResources, chunks, totalCounts, List.of());
        ScanCacheEntry createdScan = new ScanCacheEntry(createdResult);

        this.activeScan = createdScan;
        this.scanResult = createdResult;
        this.selectedResourceId = null;
        this.listScrollOffset = 0;
        this.mineralMapDirty = true;
        this.cachedStoneLayerSampler = null;
        this.actualRockStackCache.clear();
        refreshBedrockFluidTotals();
        refreshBedrockOreTotals();
        refreshResourceList();
    }

    private void refreshBedrockFluidTotals() {
        bedrockFluidTotals.clear();

        for (int chunkGridX = 0; chunkGridX < CHUNK_GRID_SIZE; chunkGridX++) {
            for (int chunkGridZ = 0; chunkGridZ < CHUNK_GRID_SIZE; chunkGridZ++) {
                BedrockFluidWorldData.BedrockFluidEntry entry = bedrockFluidEntry(startChunkX + chunkGridX, startChunkZ + chunkGridZ);

                if (entry == null || entry.definitionId() == null) {
                    continue;
                }

                bedrockFluidTotals.merge(entry.definitionId(), 1, Integer::sum);
            }
        }

        Map<String, Integer> sortedTotals = bedrockFluidTotals.entrySet().stream()
                .sorted((left, right) -> {
                    BedrockFluidDefinition leftDefinition = BedrockFluidList.byId(left.getKey());
                    BedrockFluidDefinition rightDefinition = BedrockFluidList.byId(right.getKey());
                    String leftName = leftDefinition == null ? left.getKey() : leftDefinition.englishName();
                    String rightName = rightDefinition == null ? right.getKey() : rightDefinition.englishName();
                    return leftName.compareTo(rightName);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        bedrockFluidTotals.clear();
        bedrockFluidTotals.putAll(sortedTotals);
    }

    private void refreshBedrockOreTotals() {
        bedrockOreTotals.clear();

        for (int chunkGridX = 0; chunkGridX < CHUNK_GRID_SIZE; chunkGridX++) {
            for (int chunkGridZ = 0; chunkGridZ < CHUNK_GRID_SIZE; chunkGridZ++) {
                BedrockOreWorldData.BedrockOreEntry entry = bedrockOreEntry(startChunkX + chunkGridX, startChunkZ + chunkGridZ);

                if (entry == null || entry.definitionId() == null) {
                    continue;
                }

                bedrockOreTotals.merge(entry.definitionId(), 1, Integer::sum);
            }
        }

        Map<String, Integer> sortedTotals = bedrockOreTotals.entrySet().stream()
                .sorted((left, right) -> {
                    BedrockOreDefinition leftDefinition = BedrockOreList.byId(left.getKey());
                    BedrockOreDefinition rightDefinition = BedrockOreList.byId(right.getKey());
                    String leftName = leftDefinition == null ? left.getKey() : leftDefinition.englishName();
                    String rightName = rightDefinition == null ? right.getKey() : rightDefinition.englishName();
                    return leftName.compareTo(rightName);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        bedrockOreTotals.clear();
        bedrockOreTotals.putAll(sortedTotals);
    }

    private void scanNextColumns() {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;

        if (level == null || activeScan == null) {
            if (activeScan != null) {
                activeScan.scanningComplete = true;
            }
            return;
        }

        int scannedColumns = 0;
        boolean foundNewResource = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (activeScan.nextColumnIndex < MAP_SIZE * MAP_SIZE && scannedColumns < SCAN_COLUMNS_PER_TICK) {
            int columnIndex = activeScan.nextColumnIndex;

            ColumnScanResult columnResult = scanColumn(level, columnIndex, activeScan.result.highestResources(),
                    activeScan.result.chunks(), activeScan.result.totalCounts(), pos);
            foundNewResource |= columnResult.foundNewResource();
            activeScan.nextColumnIndex++;

            if (columnResult.scanned()) {
                scannedColumns++;
            }
        }

        if (scannedColumns > 0) {
            mineralMapDirty = true;
        }

        if (foundNewResource) {
            refreshResourceList();
        }

        if (activeScan.nextColumnIndex >= MAP_SIZE * MAP_SIZE) {
            activeScan.scanningComplete = true;
            freezeChunkCounts();
            rebuildButtons();
        }
    }

    private void refreshResourceList() {
        List<MineralResource> resources = scanResult.totalCounts().keySet().stream()
                .map(NaturalResourceList::byId)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(MineralResource::englishName))
                .toList();

        if (!resources.equals(scanResult.resources())) {
            scanResult = scanResult.withResources(resources);
            if (activeScan != null) {
                activeScan.result = scanResult;
            }
            rebuildButtons();
        }
    }

    private void freezeChunkCounts() {
        for (int chunkGridX = 0; chunkGridX < CHUNK_GRID_SIZE; chunkGridX++) {
            for (int chunkGridZ = 0; chunkGridZ < CHUNK_GRID_SIZE; chunkGridZ++) {
                ChunkScan chunk = scanResult.chunks()[chunkGridX][chunkGridZ];
                scanResult.chunks()[chunkGridX][chunkGridZ] = new ChunkScan(chunk.chunkX(), chunk.chunkZ(), Map.copyOf(chunk.counts()));
            }
        }
    }

    private ColumnScanResult scanColumn(
            Level level,
            int columnIndex,
            String[][] highestResources,
            ChunkScan[][] chunks,
            Map<String, Integer> totalCounts,
            BlockPos.MutableBlockPos pos
    ) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int mapX = columnIndex % MAP_SIZE;
        int mapZ = columnIndex / MAP_SIZE;
        int chunkGridX = mapX / CHUNK_SIZE;
        int chunkGridZ = mapZ / CHUNK_SIZE;
        int chunkX = startChunkX + chunkGridX;
        int chunkZ = startChunkZ + chunkGridZ;
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);

        if (chunk == null) {
            return ColumnScanResult.NOT_SCANNED;
        }

        int localX = mapX % CHUNK_SIZE;
        int localZ = mapZ % CHUNK_SIZE;
        String highestResource = null;
        Map<String, Integer> chunkCounts = chunks[chunkGridX][chunkGridZ].counts();
        boolean foundNewResource = false;

        for (int y = maxY - 1; y >= minY; y--) {
            pos.set(localX, y, localZ);
            BlockState state = chunk.getBlockState(pos);
            String resourceId = resourceIdFor(state);

            if (resourceId == null) {
                continue;
            }

            if (!totalCounts.containsKey(resourceId)) {
                foundNewResource = true;
            }
            chunkCounts.merge(resourceId, 1, Integer::sum);
            totalCounts.merge(resourceId, 1, Integer::sum);

            if (highestResource == null) {
                highestResource = resourceId;
            }
        }

        if (highestResource != null) {
            highestResources[mapX][mapZ] = highestResource;
        }

        return new ColumnScanResult(true, foundNewResource);
    }

    private static String resourceIdFor(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key.getPath();

        if (ROCK_BLOCK_IDS.contains(path)) {
            return null;
        }

        if (!GT6UOU.MODID.equals(key.getNamespace())) {
            return null;
        }

        String resourceId = OreBlockForm.mineralIdFromBlockId(path);

        return NaturalResourceList.byId(resourceId)
                .filter(MineralScannerScreen::isMineralScanResource)
                .map(MineralResource::id)
                .orElse(null);
    }

    private static boolean isMineralScanResource(MineralResource resource) {
        ScannerList category = resource.category();
        return category == ScannerList.DEPOSIT
                || category == ScannerList.ORE;
    }

    private static int colorFor(String resourceId) {
        if (resourceId == null) {
            return mapEmptyColor();
        }

        return switch (resourceId) {
            case "brown_clay" -> 0xFFE68C4B;
            case "yellow_clay" -> 0xFFEAF20A;
            case "red_clay" -> 0xFFF20202;
            case "blue_clay" -> 0xFF405DED;
            case "white_clay" -> 0xFFBFE8FF;
            case "oilsands" -> 0xFF3C3026;
            case "black_sand" -> 0xFF1F1F1F;
            case "basaltic_black_sand" -> 0xFF283028;
            case "granitic_black_sand" -> 0xFF302A2A;
            default -> oreColorFor(resourceId);
        };
    }

    private static int bedrockFluidColor(String fluidId) {
        return switch (fluidId) {
            case "heavy_oil_deposit" -> 0xFF120B05;
            case "light_oil_deposit" -> 0xFF5A3918;
            case "natural_gas_deposit" -> 0xFFEDEDE2;
            case "oil_deposit" -> 0xFF241608;
            case "raw_oil_deposit" -> 0xFF1A1208;
            case "salt_water_deposit" -> 0xFF83BFD6;
            default -> 0xFFCC66FF;
        };
    }

    private static int bedrockOreColor(String oreVeinId) {
        if (oreVeinId == null) {
            return mapEmptyColor();
        }

        int hash = oreVeinId.hashCode();
        int red = 80 + Math.floorMod(hash, 120);
        int green = 80 + Math.floorMod(hash >> 8, 120);
        int blue = 80 + Math.floorMod(hash >> 16, 120);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int oreColorFor(String resourceId) {
        return switch (resourceId) {
            case "almandine" -> 0xFF9E2F2F;
            case "alunite" -> 0xFFE7D7C0;
            case "amethyst" -> 0xFFA05CFF;
            case "apatite" -> 0xFF58C6D6;
            case "asbestos" -> 0xFFD8D8C0;
            case "barite" -> 0xFFE7E0D0;
            case "basaltic_mineral_sand" -> 0xFF2A2926;
            case "bastnasite" -> 0xFFC99B57;
            case "bauxite" -> 0xFFB56242;
            case "bentonite" -> 0xFFC8B28A;
            case "blue_topaz" -> 0xFF62B7FF;
            case "bornite" -> 0xFFA15A8F;
            case "calcite" -> 0xFFE8E2D2;
            case "cassiterite" -> 0xFF4A4642;
            case "cassiterite_sand" -> 0xFF6F675C;
            case "chalcocite" -> 0xFF2F353A;
            case "chalcopyrite" -> 0xFFD2A043;
            case "chromite" -> 0xFF26352C;
            case "cinnabar" -> 0xFFD12422;
            case "coal" -> 0xFF6A6A6A;
            case "cobaltite" -> 0xFF4B74C9;
            case "cooperite" -> 0xFFA7A7A0;
            case "copper" -> 0xFFD97838;
            case "diamond" -> 0xFF8EF4FF;
            case "diatomite" -> 0xFFE6E0CC;
            case "electrotine" -> 0xFF4C79FF;
            case "emerald" -> 0xFF27D663;
            case "fullers_earth" -> 0xFFB39B73;
            case "galena" -> 0xFF5F5F68;
            case "garnet_sand" -> 0xFF8E3A2F;
            case "garnierite" -> 0xFF72A85E;
            case "glauconite_sand" -> 0xFF5C8A5C;
            case "goethite" -> 0xFF9B5E2E;
            case "gold" -> 0xFFFFD34D;
            case "granitic_mineral_sand" -> 0xFF51413B;
            case "graphite" -> 0xFF2D2D2D;
            case "green_sapphire" -> 0xFF43C875;
            case "grossular" -> 0xFFC99C47;
            case "gypsum" -> 0xFFEEE8D8;
            case "hematite" -> 0xFF8F2F2F;
            case "ilmenite" -> 0xFF3D3F46;
            case "iron" -> 0xFFB48763;
            case "kyanite" -> 0xFF4D6FD6;
            case "lapis" -> 0xFF244BD9;
            case "lazurite" -> 0xFF1F3FAA;
            case "lead" -> 0xFF606975;
            case "lepidolite" -> 0xFFC58AC7;
            case "limonite" -> 0xFFB9823B;
            case "magnetite" -> 0xFF303336;
            case "magnesite" -> 0xFFDDE3D6;
            case "malachite" -> 0xFF22A95B;
            case "mica" -> 0xFFD6C68F;
            case "molybdenite" -> 0xFF5A6674;
            case "monazite" -> 0xFFB88C45;
            case "nickel" -> 0xFF8FB58B;
            case "olivine" -> 0xFF91B64C;
            case "opal" -> 0xFFC9F2EA;
            case "palladium" -> 0xFFC6CBD0;
            case "pentlandite" -> 0xFFB5A25A;
            case "pitchblende" -> 0xFF24332A;
            case "platinum" -> 0xFFD5DDE2;
            case "pollucite" -> 0xFFDAD6C8;
            case "powellite" -> 0xFFD7C46A;
            case "pyrite" -> 0xFFD3B348;
            case "pyrochlore" -> 0xFF5C463A;
            case "pyrolusite" -> 0xFF333338;
            case "pyrope" -> 0xFFB2292D;
            case "quartzite" -> 0xFFECE6D6;
            case "realgar" -> 0xFFD94426;
            case "red_garnet" -> 0xFFA82B2F;
            case "redstone" -> 0xFFFF2020;
            case "sylvite" -> 0xFFE4D2C8;
            case "ruby" -> 0xFFE62445;
            case "halite" -> 0xFFF2F2EA;
            case "saltpeter" -> 0xFFEAE7D6;
            case "sapphire" -> 0xFF2E61E8;
            case "scheelite" -> 0xFFD6D0A0;
            case "silver" -> 0xFFD4D8DA;
            case "soapstone" -> 0xFF9FB39B;
            case "sodalite" -> 0xFF3567C8;
            case "spessartine" -> 0xFFD86B2C;
            case "sphalerite" -> 0xFF7E5B36;
            case "spodumene" -> 0xFFB7D8C6;
            case "stibnite" -> 0xFF6A6B70;
            case "sulfur" -> 0xFFF2D83A;
            case "talc" -> 0xFFB9CBB5;
            case "tantalite" -> 0xFF4A3634;
            case "tetrahedrite" -> 0xFF6F5E58;
            case "tin" -> 0xFFC9C7BF;
            case "topaz" -> 0xFFF2B94C;
            case "tricalcium_phosphate" -> 0xFFE6DED0;
            case "tungstate" -> 0xFF676D73;
            case "uraninite" -> 0xFF263A25;
            case "vanadium_magnetite" -> 0xFF27302C;
            case "wulfenite" -> 0xFFE27925;
            case "yellow_garnet" -> 0xFFD8A22D;
            case "zeolite" -> 0xFFD7CEC0;
            default -> 0xFFCC66FF;
        };
    }

    private StoneLayerStackSampler stoneLayerSampler() {
        if (cachedStoneLayerSampler != null) {
            return cachedStoneLayerSampler;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return null;
        }

        if (minecraft.level.dimension().equals(DimensionList.EARTH)
                && minecraft.getSingleplayerServer() != null) {
            ServerLevel earth = minecraft.getSingleplayerServer().getLevel(DimensionList.EARTH);
            if (earth != null
                    && earth.getChunkSource().getGenerator() instanceof GTEarthChunkGenerator generator
                    && generator.earthState() != null) {
                cachedStoneLayerSampler = generator.earthState().stoneLayerSampler();
                return cachedStoneLayerSampler;
            }
        }

        long seed = minecraft.getSingleplayerServer() == null
                ? 0L
                : minecraft.getSingleplayerServer().getWorldData().worldGenOptions().seed();

        cachedStoneLayerSampler = new StoneLayerStackSampler(seed);
        return cachedStoneLayerSampler;
    }

    private static int surfaceY(int x, int z) {
        Level level = Minecraft.getInstance().level;

        if (level == null) {
            return 64;
        }

        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }

    private static List<String> actualRockStack(StoneLayerStackSampler sampler, int x, int z, int surfaceY) {
        Level level = Minecraft.getInstance().level;

        if (level == null) {
            return List.of();
        }

        Set<String> rockIds = new LinkedHashSet<>();
        String previousRockId = null;

        for (int y = surfaceY; y >= level.getMinBuildHeight(); y--) {
            String rockId = sampler.sampleRockId(x, y, z, surfaceY);

            if (!rockId.equals(previousRockId)) {
                rockIds.add(rockId);
                previousRockId = rockId;
            }
        }

        return List.copyOf(rockIds);
    }

    private static String regionLetter(RegionType regionType) {
        return switch (regionType) {
            case CONTINENTAL_SEDIMENTARY -> "C";
            case VOLCANIC -> "V";
            case UPLIFT -> "U";
            case MAFIC_DEEP -> "M";
        };
    }

    private static int regionColor(RegionType regionType) {
        return switch (regionType) {
            case CONTINENTAL_SEDIMENTARY -> 0xFF6B5A3A;
            case VOLCANIC -> 0xFF6E2F2F;
            case UPLIFT -> 0xFF485B76;
            case MAFIC_DEEP -> 0xFF30333A;
        };
    }

    private static String readableRegionName(RegionType regionType) {
        return switch (regionType) {
            case CONTINENTAL_SEDIMENTARY -> "Continental sedimentary";
            case VOLCANIC -> "Volcanic";
            case UPLIFT -> "Uplift";
            case MAFIC_DEEP -> "Mafic deep";
        };
    }

    private static String readableRockName(String rockId) {
        String[] parts = rockId.split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }

        return result.toString();
    }

    private static BedrockFluidWorldData.BedrockFluidEntry bedrockFluidEntry(int chunkX, int chunkZ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.getSingleplayerServer() == null) {
            return null;
        }

        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
        if (serverLevel == null) {
            return null;
        }

        return BedrockFluidWorldData.getOrCreate(serverLevel).getEntry(chunkX, chunkZ);
    }

    private static BedrockOreWorldData.BedrockOreEntry bedrockOreEntry(int chunkX, int chunkZ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.getSingleplayerServer() == null) {
            return null;
        }

        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
        if (serverLevel == null) {
            return null;
        }

        return BedrockOreWorldData.getOrCreate(serverLevel).getEntry(chunkX, chunkZ);
    }

    @Override
    public void onClose() {
        releaseMineralMapTexture();
        super.onClose();
    }

    @Override
    public void removed() {
        releaseMineralMapTexture();
        super.removed();
    }

    private void releaseMineralMapTexture() {
        if (mineralMapTextureId != null) {
            Minecraft.getInstance().getTextureManager().release(mineralMapTextureId);
            mineralMapTextureId = null;
            mineralMapTexture = null;
        }
    }

    private enum DisplayMode {
        MINERALS,
        STONE_CATEGORIES,
        BEDROCK_FLUIDS,
        BEDROCK_ORES;

        private DisplayMode next() {
            DisplayMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private String title() {
            return switch (this) {
                case MINERALS -> "minerals";
                case STONE_CATEGORIES -> "stone";
                case BEDROCK_FLUIDS -> "bedrock fluids";
                case BEDROCK_ORES -> "bedrock ores";
            };
        }
    }

    private record ChunkScan(int chunkX, int chunkZ, Map<String, Integer> counts) {
    }

    private record ColumnScanResult(boolean scanned, boolean foundNewResource) {
        private static final ColumnScanResult NOT_SCANNED = new ColumnScanResult(false, false);
    }

    private static final class ScanCacheEntry {
        private ScanResult result;
        private int nextColumnIndex;
        private boolean scanningComplete;

        private ScanCacheEntry(ScanResult result) {
            this.result = result;
        }
    }

    private record ScanResult(
            String[][] highestResources,
            ChunkScan[][] chunks,
            Map<String, Integer> totalCounts,
            List<MineralResource> resources
    ) {
        static ScanResult empty() {
            ChunkScan[][] chunks = new ChunkScan[CHUNK_GRID_SIZE][CHUNK_GRID_SIZE];

            for (int x = 0; x < CHUNK_GRID_SIZE; x++) {
                for (int z = 0; z < CHUNK_GRID_SIZE; z++) {
                    chunks[x][z] = new ChunkScan(0, 0, new LinkedHashMap<>());
                }
            }

            return new ScanResult(new String[MAP_SIZE][MAP_SIZE], chunks, new LinkedHashMap<>(), List.of());
        }

        String highestResourceAt(int x, int z) {
            return highestResources[x][z];
        }

        ChunkScan chunk(int chunkGridX, int chunkGridZ) {
            return chunks[chunkGridX][chunkGridZ];
        }

        int totalCount(String resourceId) {
            return totalCounts.getOrDefault(resourceId, 0);
        }

        ScanResult withResources(List<MineralResource> resources) {
            return new ScanResult(highestResources, chunks, totalCounts, resources);
        }
    }
}
