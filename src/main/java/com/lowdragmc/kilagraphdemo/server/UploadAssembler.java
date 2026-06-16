package com.lowdragmc.kilagraphdemo.server;

import com.lowdragmc.kilagraphdemo.network.Chunks;
import com.lowdragmc.kilagraphdemo.network.ModNetworking;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reassembles chunked client uploads (keyed by transfer id), then performs the upsert. In-progress
 * transfers live here (server-side, not in any UI), so they survive the client closing the browser.
 */
public final class UploadAssembler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Chunks.Accumulator> TRANSFERS = new ConcurrentHashMap<>();

    private UploadAssembler() {
    }

    /** Accept one upload chunk; on the final chunk, reassemble and upsert the work. */
    public static void accept(ServerPlayer player, String transferId, int index, int total, byte[] data) {
        if (total <= 0 || total > Chunks.MAX_CHUNKS) {
            TRANSFERS.remove(transferId);
            return;
        }
        Chunks.Accumulator acc = TRANSFERS.computeIfAbsent(transferId, k -> new Chunks.Accumulator(total));
        if (!acc.put(index, data)) return;

        TRANSFERS.remove(transferId);
        byte[] bytes = acc.assemble();
        if (bytes == null) return;
        CompoundTag packageTag;
        try {
            packageTag = Chunks.toTag(bytes);
        } catch (IOException e) {
            LOGGER.warn("[KilaGraphDemo] failed to parse uploaded package from {}", player.getName().getString(), e);
            return;
        }

        WorksSavedData data2 = WorksSavedData.get((ServerLevel) player.level());
        WorksSavedData.UploadResult result = data2.upsert(player, packageTag);
        switch (result.status()) {
            case PUBLISHED -> player.sendSystemMessage(Component.literal("Work published."));
            case UPDATED -> player.sendSystemMessage(Component.literal("Work updated."));
            case REJECTED_HAS_OTHER ->
                    player.sendSystemMessage(Component.literal("You already published a different work — delete it first."));
        }
        ModNetworking.sendList(player);
    }
}
