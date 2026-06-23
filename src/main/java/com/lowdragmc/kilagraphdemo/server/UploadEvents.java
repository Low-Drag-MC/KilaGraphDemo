package com.lowdragmc.kilagraphdemo.server;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server (game-bus) event handling for uploads: when a player disconnects, drop any partial in-flight
 * transfers they left buffered in {@link UploadAssembler} so a half-finished upload can't pin memory.
 */
@EventBusSubscriber(modid = Kilagraphdemo.MODID)
public final class UploadEvents {
    private UploadEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UploadAssembler.clearPlayer(player.getUUID());
        }
    }
}
