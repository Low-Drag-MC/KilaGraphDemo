package com.lowdragmc.kilagraphdemo;

import com.lowdragmc.kilagraphdemo.drone.DroneGameTests;
import com.lowdragmc.kilagraphdemo.network.ModNetworking;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.mojang.logging.LogUtils;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.io.File;

@Mod(Kilagraphdemo.MODID)
public class Kilagraphdemo {
    public static final String MODID = "kilagraphdemo";
    /** Max works a normal player may publish to a server (see {@link #canBypassUploadLimit}). Static knob. */
    public static int MAX_WORKS_PER_PLAYER = 1;
    /** Server-side cap on an upload's assembled (compressed) payload bytes. Client caps are advisory. */
    public static int MAX_UPLOAD_BYTES = 8 * 1024 * 1024;
    /** Ceiling for NBT decompression (guards against decompression bombs from a malicious client). */
    public static int MAX_DECOMPRESSED_BYTES = 32 * 1024 * 1024;
    /** Minimum gap between a player's completed uploads (publishes and updates both count). */
    public static long UPLOAD_COOLDOWN_MS = 10_000L;
    /** Max simultaneous in-flight transfers a single player may have buffered server-side. */
    public static int MAX_CONCURRENT_TRANSFERS = 2;
    /** A partial transfer with no activity for this long is swept (abandoned/disconnected upload). */
    public static long TRANSFER_TIMEOUT_MS = 30_000L;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static File ASSETS_PATH;

    public Kilagraphdemo(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.init(modEventBus);
        modEventBus.addListener(ModNetworking::register);
        DroneGameTests.init(modEventBus);
        ASSETS_PATH = new File(LDLib2.getAssetsDir(), MODID);
        if (ASSETS_PATH.mkdirs()) {
            LOGGER.info("Assets directory created: {}", ASSETS_PATH.getAbsolutePath());
        }
    }

    public static File getAssetsDir() {
        return ASSETS_PATH;
    }

    /**
     * Whether {@code player} is exempt from the {@link #MAX_WORKS_PER_PLAYER} upload quota — Creative or Op
     * (gamemaster). Works on both {@code ServerPlayer} and the client {@code LocalPlayer} (permissions are
     * synced), so the same check gates the upload server-side and in the browser UI.
     */
    public static boolean canBypassUploadLimit(Player player) {
        return player.isCreative() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
