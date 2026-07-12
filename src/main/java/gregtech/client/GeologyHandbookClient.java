package gregtech.client;

import net.minecraft.client.Minecraft;

public final class GeologyHandbookClient {
    private GeologyHandbookClient() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GeologyHandbookScreen());
    }
}
