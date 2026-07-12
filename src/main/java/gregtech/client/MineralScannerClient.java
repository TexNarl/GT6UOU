package gregtech.client;

import net.minecraft.client.Minecraft;

public final class MineralScannerClient {
    private MineralScannerClient() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new MineralScannerScreen());
    }
}
