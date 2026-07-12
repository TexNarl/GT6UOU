package gregtech.dev;

import gregtech.worldgen.GTDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import gregtech.registry.GTItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;

public final class DevWorldGiftHandler {
    private static final String DEV_WORLD_PREFIX = "GT6UOU Dev ";
    private static final String EARTH_DEV_WORLD_PREFIX = "GT6UOU Earth ";
    private static final Set<String> MARKED_DEV_WORLD_NAMES = new HashSet<>();
    private static final Set<String> MARKED_EARTH_DEV_WORLD_NAMES = new HashSet<>();
    private static final Set<String> GIFTED_PLAYERS = new HashSet<>();
    private static final Set<String> EARTH_TELEPORTED_PLAYERS = new HashSet<>();

    private DevWorldGiftHandler() {
    }

    public static void markDevWorld(String levelName) {
        MARKED_DEV_WORLD_NAMES.add(levelName);
    }

    public static void markEarthDevWorld(String levelName) {
        MARKED_EARTH_DEV_WORLD_NAMES.add(levelName);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        String levelName = player.server.getWorldData().getLevelName();

        boolean devWorld = levelName.startsWith(DEV_WORLD_PREFIX) || MARKED_DEV_WORLD_NAMES.contains(levelName);
        boolean earthDevWorld = levelName.startsWith(EARTH_DEV_WORLD_PREFIX) || MARKED_EARTH_DEV_WORLD_NAMES.contains(levelName);

        if (!devWorld && !earthDevWorld) {
            return;
        }

        if (earthDevWorld) {
            teleportToEarthOnce(player, levelName);
        }

        String giftKey = levelName + ":" + player.getUUID();

        if (!GIFTED_PLAYERS.add(giftKey)) {
            return;
        }

        giveIfMissing(player, GTItems.STRATA_CUTTING_TOOL.get());
        giveIfMissing(player, GTItems.DAY_CLOCK.get());
        giveIfMissing(player, GTItems.MINERAL_SCANNER.get());
        giveIfMissing(player, GTItems.ORE_REVEAL_TOOL.get());
        giveIfMissing(player, GTItems.GEOLOGY_HANDBOOK.get());
    }

    private static void teleportToEarthOnce(ServerPlayer player, String levelName) {
        String teleportKey = levelName + ":" + player.getUUID();

        if (!EARTH_TELEPORTED_PLAYERS.add(teleportKey) || player.level().dimension().equals(GTDimensions.EARTH)) {
            return;
        }

        ServerLevel earth = player.server.getLevel(GTDimensions.EARTH);

        if (earth == null) {
            player.displayClientMessage(Component.literal("GT Earth dimension is not loaded: gt6uou:earth"), false);
            return;
        }

        player.teleportTo(earth, 0.5, 160.0, 0.5, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.literal("Entered GT Earth (gt6uou:earth)."), false);
    }

    private static void giveIfMissing(ServerPlayer player, Item item) {
        if (player.getInventory().contains(new ItemStack(item))) {
            return;
        }

        player.getInventory().add(new ItemStack(item));
    }
}
