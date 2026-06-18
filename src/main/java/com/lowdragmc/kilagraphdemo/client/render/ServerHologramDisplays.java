package com.lowdragmc.kilagraphdemo.client.render;

import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import com.lowdragmc.kilagraphdemo.graph.LocalGraphStore;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of server-hologram displays keyed by work uid, plus the lazy-pull logic. A server
 * hologram renders whatever uid the server synced to its block entity; the first time a client must render
 * a uid it lacks locally, this triggers a chunked download (via {@link ClientWorks}) and reports progress
 * so the renderer can draw a {@code NN%} placeholder. Once the download lands in {@link LocalGraphStore},
 * the next {@link #resolve} builds and caches the {@link HologramDisplay}.
 *
 * <p>All methods run on the client/render thread (single-threaded), so plain maps are fine.</p>
 */
public final class ServerHologramDisplays {

    /** Don't re-send a download request for the same uid more often than this (ms). */
    private static final long REQUEST_COOLDOWN_MS = 3_000L;

    private static final Map<String, HologramDisplay> CACHE = new HashMap<>();
    private static final Map<String, Long> LAST_REQUEST = new HashMap<>();

    private ServerHologramDisplays() {
    }

    /** What a server hologram should render for {@code uid}: a ready display, or a download in progress. */
    public record Resolved(@Nullable HologramDisplay display, float progress) {
        static final Resolved NONE = new Resolved(null, -1f);
    }

    public static Resolved resolve(@Nullable String uid) {
        if (uid == null || uid.isEmpty()) return Resolved.NONE;

        HologramDisplay cached = CACHE.get(uid);
        if (cached != null) return new Resolved(cached, 1f);

        // Locally available? Build + cache the display.
        var pkg = LocalGraphStore.load(uid);
        if (pkg.isPresent()) {
            HologramDisplay display = new HologramDisplay(pkg.get().loadGraph(),
                    pkg.get().model().toContent(), pkg.get().model().renderRadius());
            CACHE.put(uid, display);
            LAST_REQUEST.remove(uid);
            return new Resolved(display, 1f);
        }

        // Missing locally — pull it (once), and report download progress meanwhile.
        float progress = ClientWorks.downloadProgress(uid);
        if (progress < 0f) {
            long now = System.currentTimeMillis();
            Long last = LAST_REQUEST.get(uid);
            if (last == null || now - last > REQUEST_COOLDOWN_MS) {
                ClientWorks.download(uid);
                LAST_REQUEST.put(uid, now);
            }
            return new Resolved(null, 0f);
        }
        return new Resolved(null, progress);
    }

    /** Drop all cached displays (level unload / disconnect). */
    public static void clearAll() {
        CACHE.values().forEach(HologramDisplay::close);
        CACHE.clear();
        LAST_REQUEST.clear();
    }
}
