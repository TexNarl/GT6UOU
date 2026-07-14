package gregtech.client.earth;

import com.mojang.blaze3d.platform.NativeImage;
import gregtech.dev.DevWorldLauncher;
import gregapi.data.DimensionList;
import gregtech.worldgen.earth.EarthGeneratorState;
import gregtech.worldgen.earth.preview.EarthPreviewService;
import gregtech.worldgen.earth.preview.EarthPreviewService.LegendEntry;
import gregtech.worldgen.earth.preview.EarthPreviewService.PointInfo;
import gregtech.worldgen.earth.preview.EarthPreviewService.PreviewLayer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.levelgen.WorldOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive create-world frontend for the shared Earth preview pipeline.
 *
 * <p>The screen deliberately contains no terrain rules. Rendering, legends
 * and point inspection all query {@link EarthPreviewService}, which samples
 * the same seed-derived state as the physical {@code gt6uou:earth} world.</p>
 */
public final class EarthCreateWorldPreviewScreen extends Screen {
    private static final int PREVIEW_PIXELS = 256;
    private static final int BLOCKS_PER_PIXEL = 128;
    private static final int SIDE_WIDTH = 270;
    private static final int PANEL_COLOR = 0xF0111317;
    private static final int MAP_BORDER_COLOR = 0xFF747B86;
    private static final int TEXT_COLOR = 0xFFE9EDF2;
    private static final int MUTED_TEXT_COLOR = 0xFFAAB1BC;
    private static final int ACCENT_COLOR = 0xFFFFD36A;
    private static final int TOOLTIP_COLOR = 0xE0101115;

    private static final PreviewLayer[] VISUALIZERS = {
            PreviewLayer.RIVERS_AND_MOUNTAINS,
            PreviewLayer.GEOLOGY,
            PreviewLayer.ROCKS,
            PreviewLayer.BIOMES,
            PreviewLayer.RAINFALL,
            PreviewLayer.TEMPERATURE,
            PreviewLayer.CLIMATE_RESTRICTED_GENERATION,
            PreviewLayer.BIOME_ALTITUDE,
            PreviewLayer.INLAND_HEIGHT
    };

    private final Screen parent;
    private String seedText = "";
    private PreviewLayer selectedLayer = PreviewLayer.RIVERS_AND_MOUNTAINS;
    private PreviewLayer appliedLayer;
    private String status = "Waiting for preview";

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int previewSize;
    private int mapLeft;
    private int mapTop;
    private int sideLeft;

    private EditBox seedField;
    private Button visualizerButton;
    private Button createButton;
    private Button exportButton;

    private long requestId;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private CompletableFuture<?> renderFuture;
    private DynamicTexture previewTexture;
    private ResourceLocation previewTextureId;
    private BufferedImage previewImage;
    private EarthGeneratorState appliedState;
    private PreviewRequest appliedRequest;
    private PointInfo selectedInfo;
    private SelectedPoint selectedPoint;

    public EarthCreateWorldPreviewScreen(Screen parent) {
        super(Component.literal("GT Earth World Preview"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        previewSize = Math.max(180, Math.min(384, Math.min(height - 72, width - SIDE_WIDTH - 52)));
        panelWidth = previewSize + SIDE_WIDTH + 30;
        panelHeight = previewSize + 54;
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        mapLeft = panelLeft + 12;
        mapTop = panelTop + 26;
        sideLeft = mapLeft + previewSize + 14;

        int y = mapTop;
        visualizerButton = addRenderableWidget(Button.builder(visualizerLabel(), button -> selectNextVisualizer())
                .bounds(sideLeft, y, SIDE_WIDTH - 12, 20)
                .build());
        y += 34;

        seedField = new EditBox(font, sideLeft, y, SIDE_WIDTH - 12, 20, Component.literal("World seed"));
        seedField.setMaxLength(64);
        seedField.setValue(seedText);
        seedField.setHint(Component.literal("Leave blank for a random seed"));
        seedField.setResponder(value -> seedText = value);
        addRenderableWidget(seedField);
        y += 24;

        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyPreview())
                .bounds(sideLeft, y, SIDE_WIDTH - 12, 20)
                .build());
        y += 24;

        exportButton = addRenderableWidget(Button.builder(Component.literal("Export Preview"), button -> exportPreview())
                .bounds(sideLeft, y, SIDE_WIDTH - 12, 20)
                .build());
        exportButton.active = previewImage != null;

        int bottomY = mapTop + previewSize + 7;
        int halfWidth = (panelWidth - 24 - 4) / 2;
        createButton = addRenderableWidget(Button.builder(Component.literal("Create Earth world"), button -> createWorld())
                .bounds(mapLeft, bottomY, halfWidth, 20)
                .build());
        createButton.active = appliedRequest != null;
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(mapLeft + halfWidth + 4, bottomY, panelWidth - 24 - halfWidth - 4, 20)
                .build());

        if (appliedState == null && (renderFuture == null || renderFuture.isDone())) {
            applyPreview();
        }
    }

    private void selectNextVisualizer() {
        int current = 0;
        for (int index = 0; index < VISUALIZERS.length; index++) {
            if (VISUALIZERS[index] == selectedLayer) {
                current = index;
                break;
            }
        }
        selectedLayer = VISUALIZERS[(current + 1) % VISUALIZERS.length];
        visualizerButton.setMessage(visualizerLabel());
        status = "Visualizer selected; press Apply";
    }

    private Component visualizerLabel() {
        return Component.literal("Visualizer: " + selectedLayer.displayName());
    }

    private void applyPreview() {
        PreviewRequest request = parseRequest();
        if (request == null) {
            return;
        }

        cancelGeneration();
        long currentRequest = ++requestId;
        AtomicBoolean currentCancellation = new AtomicBoolean();
        cancellation = currentCancellation;
        status = "Generating " + request.layer().displayName() + "...";
        selectedInfo = null;
        selectedPoint = null;
        if (createButton != null) createButton.active = false;
        if (exportButton != null) exportButton.active = false;

        renderFuture = CompletableFuture.supplyAsync(() -> {
            long started = System.nanoTime();
            EarthGeneratorState state = EarthGeneratorState.create(DimensionList.EARTH, request.seed());
            BufferedImage image = EarthPreviewService.render(
                    state,
                    request.centerX(),
                    request.centerZ(),
                    PREVIEW_PIXELS,
                    request.blocksPerPixel(),
                    request.layer(),
                    currentCancellation::get
            );
            return new PreviewResult(request, state, image, System.nanoTime() - started);
        }, Util.backgroundExecutor()).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            if (currentRequest != requestId || currentCancellation.get() || Minecraft.getInstance().screen != this) {
                return;
            }
            if (error != null) {
                status = "Preview failed: " + rootMessage(error);
                createButton.active = appliedRequest != null;
                exportButton.active = previewImage != null;
                return;
            }

            installTexture(result.image());
            previewImage = result.image();
            appliedState = result.state();
            appliedRequest = result.request();
            appliedLayer = result.request().layer();
            int span = PREVIEW_PIXELS * result.request().blocksPerPixel();
            status = String.format(java.util.Locale.ROOT, "%d x %d blocks | %.2f s",
                    span, span, result.elapsedNanos() / 1_000_000_000.0D);
            createButton.active = true;
            exportButton.active = true;
        }));
    }

    private PreviewRequest parseRequest() {
        String value = seedText.trim();
        OptionalLong parsed = value.isEmpty() ? OptionalLong.empty() : WorldOptions.parseSeed(value);
        long seed = parsed.orElseGet(WorldOptions::randomSeed);
        return new PreviewRequest(seed, 0, 0, BLOCKS_PER_PIXEL, selectedLayer);
    }

    private void createWorld() {
        if (appliedRequest == null) {
            status = "Press Apply before creating the world";
            return;
        }
        cancelGeneration();
        DevWorldLauncher.createEarthDevWorld(this, appliedRequest.seed());
    }

    private void exportPreview() {
        if (previewImage == null || appliedRequest == null || appliedLayer == null) {
            status = "Nothing has been rendered yet";
            return;
        }
        try {
            Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("earth-previews");
            Files.createDirectories(directory);
            String fileName = "earth_seed_" + appliedRequest.seed()
                    + "_" + appliedLayer.serializedName()
                    + "_" + PREVIEW_PIXELS + "px_"
                    + BLOCKS_PER_PIXEL + "bpp.png";
            Path target = directory.resolve(fileName);
            if (!ImageIO.write(previewImage, "PNG", target.toFile())) {
                throw new IOException("No PNG writer is available");
            }
            status = "Exported: earth-previews/" + fileName;
        } catch (IOException exception) {
            status = "Export failed: " + exception.getMessage();
        }
    }

    private void installTexture(BufferedImage image) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixelRGBA(x, y, nativeImageColor(image.getRGB(x, y)));
            }
        }

        releaseTexture();
        previewTexture = new DynamicTexture(nativeImage);
        previewTextureId = Minecraft.getInstance().getTextureManager()
                .register("gt6uou_earth_create_preview", previewTexture);
    }

    private static int nativeImageColor(int argb) {
        int alpha = argb & 0xFF000000;
        int red = (argb >> 16) & 0xFF;
        int green = argb & 0x0000FF00;
        int blue = (argb & 0xFF) << 16;
        return alpha | blue | green | red;
    }

    private void cancelGeneration() {
        cancellation.set(true);
        if (renderFuture != null) {
            renderFuture.cancel(true);
            renderFuture = null;
        }
    }

    private void releaseTexture() {
        if (previewTextureId != null) {
            Minecraft.getInstance().getTextureManager().release(previewTextureId);
        }
        previewTexture = null;
        previewTextureId = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && appliedState != null && appliedLayer != null && isOverMap(mouseX, mouseY)) {
            SelectedPoint point = mapPoint(mouseX, mouseY);
            selectedPoint = point;
            selectedInfo = appliedLayer.inspect(appliedState, point.blockX(), point.blockZ());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft && mouseX < mapLeft + previewSize
                && mouseY >= mapTop && mouseY < mapTop + previewSize;
    }

    private SelectedPoint mapPoint(double mouseX, double mouseY) {
        int pixelX = Math.clamp((int) ((mouseX - mapLeft) * PREVIEW_PIXELS / previewSize), 0, PREVIEW_PIXELS - 1);
        int pixelZ = Math.clamp((int) ((mouseY - mapTop) * PREVIEW_PIXELS / previewSize), 0, PREVIEW_PIXELS - 1);
        int halfSpan = PREVIEW_PIXELS * appliedRequest.blocksPerPixel() / 2;
        int blockX = appliedRequest.centerX() - halfSpan + pixelX * appliedRequest.blocksPerPixel()
                + appliedRequest.blocksPerPixel() / 2;
        int blockZ = appliedRequest.centerZ() - halfSpan + pixelZ * appliedRequest.blocksPerPixel()
                + appliedRequest.blocksPerPixel() / 2;
        return new SelectedPoint(blockX, blockZ);
    }

    @Override
    public void onClose() {
        cancelGeneration();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        cancelGeneration();
        releaseTexture();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD8080A0D);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL_COLOR);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 8, ACCENT_COLOR);
        graphics.fill(mapLeft - 1, mapTop - 1, mapLeft + previewSize + 1, mapTop + previewSize + 1, MAP_BORDER_COLOR);

        if (previewTextureId != null) {
            graphics.blit(
                    previewTextureId,
                    mapLeft, mapTop,
                    previewSize, previewSize,
                    0.0F, 0.0F,
                    PREVIEW_PIXELS, PREVIEW_PIXELS,
                    PREVIEW_PIXELS, PREVIEW_PIXELS
            );
        } else {
            graphics.fill(mapLeft, mapTop, mapLeft + previewSize, mapTop + previewSize, 0xFF171A1F);
            graphics.drawCenteredString(font, "Generating...", mapLeft + previewSize / 2,
                    mapTop + previewSize / 2 - 4, MUTED_TEXT_COLOR);
        }

        graphics.drawString(font, "World seed", sideLeft, mapTop + 24, TEXT_COLOR, false);
        drawInformationPanel(graphics);

        if (selectedPoint != null) {
            drawSelectedMarker(graphics, selectedPoint);
        }
        if (appliedRequest != null && isOverMap(mouseX, mouseY)) {
            SelectedPoint hovered = mapPoint(mouseX, mouseY);
            drawCoordinateTooltip(graphics, mouseX, mouseY, hovered);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawInformationPanel(GuiGraphics graphics) {
        int y = mapTop + 112;
        int textWidth = SIDE_WIDTH - 18;
        if (appliedRequest != null) {
            graphics.drawString(font, "Current seed: " + appliedRequest.seed(), sideLeft, y, ACCENT_COLOR, false);
            y += 12;
        }
        if (appliedLayer != null) {
            graphics.drawString(font, "Visualizer: " + appliedLayer.displayName(), sideLeft, y, TEXT_COLOR, false);
            y += 14;
            graphics.drawString(font, "Color key", sideLeft, y, MUTED_TEXT_COLOR, false);
            y += 12;
            for (LegendEntry entry : appliedLayer.legend()) {
                if (y + 10 >= mapTop + previewSize) break;
                graphics.fill(sideLeft, y + 1, sideLeft + 8, y + 9, entry.color());
                graphics.drawString(font, entry.label(), sideLeft + 12, y, TEXT_COLOR, false);
                y += 11;
            }
        }

        y += 4;
        if (selectedPoint == null || selectedInfo == null) {
            if (y + 10 < mapTop + previewSize) {
                graphics.drawString(font, "Click the map to inspect a point", sideLeft, y, MUTED_TEXT_COLOR, false);
            }
        } else {
            graphics.drawString(font, "Selected: " + selectedPoint.blockX() + ", " + selectedPoint.blockZ(),
                    sideLeft, y, ACCENT_COLOR, false);
            y += 12;
            for (String line : selectedInfo.lines()) {
                List<FormattedCharSequence> wrapped = font.split(Component.literal(line), textWidth);
                for (FormattedCharSequence sequence : wrapped) {
                    if (y + 10 >= mapTop + previewSize) break;
                    graphics.drawString(font, sequence, sideLeft, y, TEXT_COLOR, false);
                    y += 10;
                }
                if (y + 10 >= mapTop + previewSize) break;
            }
        }

        String visibleStatus = font.plainSubstrByWidth(status, SIDE_WIDTH - 12);
        graphics.drawString(font, visibleStatus, sideLeft, mapTop + previewSize - 10, MUTED_TEXT_COLOR, false);
    }

    private void drawCoordinateTooltip(GuiGraphics graphics, int mouseX, int mouseY, SelectedPoint point) {
        String coordinate = "X: " + point.blockX() + "  Z: " + point.blockZ();
        int tooltipWidth = font.width(coordinate) + 8;
        int x = Math.min(mouseX + 10, mapLeft + previewSize - tooltipWidth - 2);
        int y = Math.min(mouseY + 10, mapTop + previewSize - 14);
        graphics.fill(x, y, x + tooltipWidth, y + 13, TOOLTIP_COLOR);
        graphics.drawString(font, coordinate, x + 4, y + 3, TEXT_COLOR, false);
    }

    private void drawSelectedMarker(GuiGraphics graphics, SelectedPoint point) {
        if (appliedRequest == null) return;
        int halfSpan = PREVIEW_PIXELS * appliedRequest.blocksPerPixel() / 2;
        double pixelX = (point.blockX() - (appliedRequest.centerX() - halfSpan))
                / (double) appliedRequest.blocksPerPixel();
        double pixelZ = (point.blockZ() - (appliedRequest.centerZ() - halfSpan))
                / (double) appliedRequest.blocksPerPixel();
        int x = mapLeft + (int) Math.round(pixelX * previewSize / PREVIEW_PIXELS);
        int y = mapTop + (int) Math.round(pixelZ * previewSize / PREVIEW_PIXELS);
        graphics.fill(x - 3, y, x + 4, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y - 3, x + 1, y + 4, 0xFFFFFFFF);
        graphics.fill(x - 2, y, x + 3, y + 1, 0xFF000000);
        graphics.fill(x, y - 2, x + 1, y + 3, 0xFF000000);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private record PreviewRequest(
            long seed,
            int centerX,
            int centerZ,
            int blocksPerPixel,
            PreviewLayer layer
    ) {
    }

    private record PreviewResult(
            PreviewRequest request,
            EarthGeneratorState state,
            BufferedImage image,
            long elapsedNanos
    ) {
    }

    private record SelectedPoint(int blockX, int blockZ) {
    }
}
