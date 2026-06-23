package com.lowdragmc.kilagraphdemo.server;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reassembles chunked client uploads (keyed by transfer id), then performs the upsert. In-progress
 * transfers live here (server-side, not in any UI), so they survive the client closing the browser.
 *
 * <p>Hardened against abuse: each chunk must be {@code <=} {@link Chunks#CHUNK_SIZE}, a transfer's
 * cumulative bytes are capped at {@link Kilagraphdemo#MAX_UPLOAD_BYTES}, a player may only have
 * {@link Kilagraphdemo#MAX_CONCURRENT_TRANSFERS} in-flight transfers, completed uploads are rate-limited
 * by {@link Kilagraphdemo#UPLOAD_COOLDOWN_MS} (publishes and updates alike), and abandoned partial
 * transfers are swept after {@link Kilagraphdemo#TRANSFER_TIMEOUT_MS}.</p>
 */
public final class UploadAssembler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** In-progress transfers, keyed by client transfer id. */
    private static final Map<String, TransferState> TRANSFERS = new ConcurrentHashMap<>();
    /** Last completed-upload time per player, for the upload cooldown (publishes and updates). */
    private static final Map<UUID, Long> LAST_UPLOAD = new ConcurrentHashMap<>();

    private UploadAssembler() {
    }

    /** One in-flight transfer: who owns it, its accumulator, bytes buffered so far, and last-touched time. */
    private static final class TransferState {
        final UUID player;
        final Chunks.Accumulator acc;
        long bytes;
        long lastActivityMs;

        TransferState(UUID player, int totalChunks, long now) {
            this.player = player;
            this.acc = new Chunks.Accumulator(totalChunks);
            this.lastActivityMs = now;
        }
    }

    /** Accept one upload chunk; on the final chunk, reassemble and upsert the work. */
    public static void accept(ServerPlayer player, String transferId, int index, int total, byte[] data) {
        long now = System.currentTimeMillis();
        sweep(now);

        UUID who = player.getUUID();
        if (total <= 0 || total > Chunks.MAX_CHUNKS) {
            TRANSFERS.remove(transferId);
            return;
        }
        // Per-chunk size guard: closes the gap where the packet decoder allows oversized byte arrays.
        if (data.length > Chunks.CHUNK_SIZE) {
            TRANSFERS.remove(transferId);
            player.sendSystemMessage(Component.translatable("message.kilagraphdemo.upload_chunk_too_large"));
            return;
        }

        TransferState state = TRANSFERS.get(transferId);
        if (state == null) {
            // New transfer — gate before buffering anything.
            Long last = LAST_UPLOAD.get(who);
            if (last != null && now - last < Kilagraphdemo.UPLOAD_COOLDOWN_MS) {
                long wait = (Kilagraphdemo.UPLOAD_COOLDOWN_MS - (now - last) + 999) / 1000;
                player.sendSystemMessage(Component.translatable(
                        "message.kilagraphdemo.upload_cooldown", wait));
                return;
            }
            if (concurrentTransfers(who) >= Kilagraphdemo.MAX_CONCURRENT_TRANSFERS) {
                player.sendSystemMessage(Component.translatable("message.kilagraphdemo.upload_too_many"));
                return;
            }
            state = new TransferState(who, total, now);
            TRANSFERS.put(transferId, state);
        }
        state.lastActivityMs = now;

        int before = state.acc.received();
        boolean complete = state.acc.put(index, data);
        if (state.acc.received() > before) {
            state.bytes += data.length;
        }
        if (state.bytes > Kilagraphdemo.MAX_UPLOAD_BYTES) {
            TRANSFERS.remove(transferId);
            player.sendSystemMessage(Component.translatable("message.kilagraphdemo.upload_work_too_large"));
            return;
        }
        if (!complete) return;

        TRANSFERS.remove(transferId);
        byte[] bytes = state.acc.assemble();
        if (bytes == null) return;
        CompoundTag packageTag;
        try {
            packageTag = Chunks.toTag(bytes);
        } catch (IOException | RuntimeException e) {
            // RuntimeException covers the NbtAccounter tripping on an oversized/decompression-bomb payload.
            LOGGER.warn("[KilaGraphDemo] failed to parse uploaded package from {}", player.getName().getString(), e);
            player.sendSystemMessage(Component.translatable("message.kilagraphdemo.upload_malformed"));
            return;
        }

        WorksSavedData data2 = WorksSavedData.get((ServerLevel) player.level());
        WorksSavedData.UploadResult result = data2.upsert(player, packageTag);
        // Cooldown applies to any accepted upload (publish or update) so re-uploads can't be spammed.
        if (result.status() != WorksSavedData.UploadStatus.REJECTED_LIMIT) {
            LAST_UPLOAD.put(who, now);
        }
        switch (result.status()) {
            case PUBLISHED -> player.sendSystemMessage(Component.translatable("message.kilagraphdemo.work_published"));
            case UPDATED -> {
                player.sendSystemMessage(Component.translatable("message.kilagraphdemo.work_updated"));
                // Notify other players so any placed hologram/projector showing this work re-pulls it.
                if (result.meta() != null) {
                    ModNetworking.broadcastWorkUpdated(player, result.meta().uid(), result.meta().version());
                }
            }
            case REJECTED_LIMIT -> player.sendSystemMessage(Component.translatable(
                    "message.kilagraphdemo.upload_limit", Kilagraphdemo.MAX_WORKS_PER_PLAYER));
        }
        ModNetworking.sendList(player);
    }

    /** Drop any in-flight transfers a player left behind (called when they disconnect). */
    public static void clearPlayer(UUID player) {
        TRANSFERS.values().removeIf(s -> s.player.equals(player));
    }

    private static int concurrentTransfers(UUID player) {
        int n = 0;
        for (TransferState s : TRANSFERS.values()) {
            if (s.player.equals(player)) n++;
        }
        return n;
    }

    /** Evict abandoned partial transfers and stale cooldown entries to bound memory. */
    private static void sweep(long now) {
        TRANSFERS.values().removeIf(s -> now - s.lastActivityMs > Kilagraphdemo.TRANSFER_TIMEOUT_MS);
        LAST_UPLOAD.values().removeIf(t -> now - t > Kilagraphdemo.UPLOAD_COOLDOWN_MS);
    }
}
