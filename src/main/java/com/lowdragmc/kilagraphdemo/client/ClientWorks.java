package com.lowdragmc.kilagraphdemo.client;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.client.editor.LocalShaderFunctions;
import com.lowdragmc.kilagraphdemo.client.render.ServerHologramDisplays;
import com.lowdragmc.kilagraphdemo.graph.LocalGraphStore;
import com.lowdragmc.kilagraphdemo.graph.ServerWorkEntry;
import com.lowdragmc.kilagraphdemo.graph.WorkPackage;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.kilagraphdemo.network.C2SDelete;
import com.lowdragmc.kilagraphdemo.network.C2SDownload;
import com.lowdragmc.kilagraphdemo.network.C2SLike;
import com.lowdragmc.kilagraphdemo.network.C2SRequestList;
import com.lowdragmc.kilagraphdemo.network.C2SUploadChunk;
import com.lowdragmc.kilagraphdemo.network.Chunks;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of the server work list, the client→server send helpers, and the receiving end of
 * chunked downloads. In-progress downloads live in a static map (keyed by work uid), so closing and
 * reopening the browser doesn't interrupt them — the new UI just re-reads {@link #downloadProgress}.
 */
public final class ClientWorks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Notified on the client thread when the list updates, a download progresses, or one completes. */
    public interface Listener {
        /** List refreshed, or a download finished (then {@code downloadedUid != null}). */
        void onWorksUpdated(@Nullable String downloadedUid);

        /** A download chunk arrived for {@code uid} (for live progress). */
        default void onDownloadProgress(String uid) {
        }
    }

    private static List<ServerWorkEntry> serverWorks = List.of();
    private static final Map<String, Chunks.Accumulator> DOWNLOADS = new ConcurrentHashMap<>();
    /** Latest server-side version we know per work uid (from the work list + update notices). Lets placed
     *  blocks tell when a locally-cached copy is stale and must be re-pulled. */
    private static final Map<String, Integer> SERVER_VERSIONS = new ConcurrentHashMap<>();
    @Nullable
    private static Listener listener;

    private ClientWorks() {
    }

    public static List<ServerWorkEntry> serverWorks() {
        return serverWorks;
    }

    public static void setListener(@Nullable Listener l) {
        listener = l;
    }

    public static boolean isDownloading(String uid) {
        return DOWNLOADS.containsKey(uid);
    }

    /** Download progress for {@code uid} in 0..1, or -1 if no transfer is in progress. */
    public static float downloadProgress(String uid) {
        Chunks.Accumulator acc = DOWNLOADS.get(uid);
        return acc == null ? -1f : acc.progress();
    }

    /** The latest server version known for {@code uid}, or -1 if unknown. A locally-cached copy older than
     *  this has been superseded by a re-upload and should be re-pulled before rendering. */
    public static int serverVersion(String uid) {
        return SERVER_VERSIONS.getOrDefault(uid, -1);
    }

    // ---- client -> server --------------------------------------------------------------------

    public static void requestList() {
        ClientPacketDistributor.sendToServer(new C2SRequestList());
    }

    public static void upload(WorkPackage pkg) {
        String transferId = UUID.randomUUID().toString();
        List<byte[]> chunks = Chunks.split(Chunks.toBytes(pkg.toTag()));
        for (int i = 0; i < chunks.size(); i++) {
            ClientPacketDistributor.sendToServer(new C2SUploadChunk(transferId, i, chunks.size(), chunks.get(i)));
        }
    }

    public static void download(String uid) {
        // Reset any stale transfer for this uid; the server will (re)stream all chunks.
        DOWNLOADS.remove(uid);
        ClientPacketDistributor.sendToServer(new C2SDownload(uid));
    }

    public static void delete(String uid) {
        ClientPacketDistributor.sendToServer(new C2SDelete(uid));
    }

    public static void setLike(String uid, boolean like) {
        ClientPacketDistributor.sendToServer(new C2SLike(uid, like));
    }

    // ---- server -> client (packet handlers) --------------------------------------------------

    public static void onListReceived(List<ServerWorkEntry> entries) {
        serverWorks = entries;
        for (ServerWorkEntry e : entries) {
            SERVER_VERSIONS.merge(e.meta().uid(), e.meta().version(), Math::max);
        }
        runOnClient(() -> notifyUpdated(null));
    }

    /**
     * A work was re-uploaded server-side (tiny {@code uid}+{@code version} notice). Record the new version and
     * drop any placed-block display cached for it, so the next render sees the stale local copy and re-pulls
     * the fresh payload. We don't download here — the heavy fetch stays lazy, driven by whatever actually
     * renders the work.
     */
    public static void onWorkUpdated(String uid, int version) {
        SERVER_VERSIONS.merge(uid, version, Math::max);
        runOnClient(() -> {
            ServerHologramDisplays.onWorkSaved(uid);
            com.lowdragmc.kilagraphdemo.slideshow.client.ClientProjectorGraphs.onWorkSaved(uid);
            notifyUpdated(null);
        });
    }

    public static void onDownloadChunk(String uid, int index, int total, byte[] data) {
        if (total <= 0 || total > Chunks.MAX_CHUNKS) {
            DOWNLOADS.remove(uid);
            return;
        }
        Chunks.Accumulator acc = DOWNLOADS.computeIfAbsent(uid, k -> new Chunks.Accumulator(total));
        boolean complete = acc.put(index, data);
        if (!complete) {
            runOnClient(() -> {
                if (listener != null) listener.onDownloadProgress(uid);
            });
            return;
        }
        DOWNLOADS.remove(uid);
        byte[] bytes = acc.assemble();
        runOnClient(() -> {
            if (bytes != null) {
                try {
                    WorkPackage pkg = WorkPackage.fromTag(Chunks.toTag(bytes));
                    cacheDependencies(pkg);
                    cacheTextures(pkg);
                    cacheModels(pkg);
                    LocalGraphStore.save(pkg);
                    // Drop any stale compiled SlideShow material for this work so it recompiles from the
                    // freshly-downloaded payload (no-op for hologram works).
                    com.lowdragmc.kilagraphdemo.slideshow.client.ClientProjectorGraphs.onWorkSaved(pkg.meta());
                    // Likewise drop any stale server-hologram display so it rebuilds from the new payload.
                    ServerHologramDisplays.onWorkSaved(pkg.meta().uid());
                } catch (IOException e) {
                    LOGGER.error("[KilaGraphDemo] failed to parse downloaded work {}", uid, e);
                    return;
                }
            }
            notifyUpdated(uid);
        });
    }

    private static void notifyUpdated(@Nullable String downloadedUid) {
        if (listener != null) listener.onWorksUpdated(downloadedUid);
    }

    /** Write a downloaded work's bundled PNG textures into the assets dir under their (rewritten)
     *  {@code kilagraphdemo:downloaded/<uid>/…} locations, so the graph binds them at render time. */
    private static void cacheTextures(WorkPackage pkg) {
        if (pkg.textures().isEmpty()) return;
        File assets = Kilagraphdemo.getAssetsDir();
        pkg.textures().forEach((location, bytes) -> {
            Identifier id = Identifier.tryParse(location);
            if (id == null) return;
            File file = new File(assets, id.getPath());
            try {
                Files.createDirectories(file.toPath().getParent());
                Files.write(file.toPath(), bytes);
                // The bundled path is stable per uid, so Minecraft's TextureManager still holds the old GPU
                // texture for this Identifier; drop it so the next getTexture() reloads the new bytes.
                Minecraft.getInstance().getTextureManager().release(id);
            } catch (IOException e) {
                LOGGER.error("[KilaGraphDemo] failed to write texture {}", location, e);
            }
        });
    }

    /** Write a downloaded work's bundled custom OBJ model into the assets dir under its (rewritten)
     *  {@code kilagraphdemo:downloaded/<uid>/model.obj} location, so {@code ObjContents} can load it. */
    private static void cacheModels(WorkPackage pkg) {
        if (pkg.models().isEmpty()) return;
        File assets = Kilagraphdemo.getAssetsDir();
        pkg.models().forEach((location, bytes) -> {
            Identifier id = Identifier.tryParse(location);
            if (id == null) return;
            File file = new File(assets, id.getPath());
            try {
                Files.createDirectories(file.toPath().getParent());
                Files.write(file.toPath(), bytes);
            } catch (IOException e) {
                LOGGER.error("[KilaGraphDemo] failed to write model {}", location, e);
            }
        });
    }

    /** Write a downloaded work's bundled Shader-Function dependencies into the local folder (so they're
     *  resolvable and editable locally, like a work you authored yourself). */
    private static void cacheDependencies(WorkPackage pkg) {
        if (pkg.resources().isEmpty()) return;
        LocalShaderFunctions store = new LocalShaderFunctions();
        pkg.resources().forEach((pathStr, tag) -> {
            IResourcePath path = IResourcePath.parse(pathStr);
            if (path != null) store.writeRaw(path, tag);
        });
    }

    /** Packet handlers run on the client thread; marshal UI-affecting work onto the main thread. */
    private static void runOnClient(Runnable r) {
        Minecraft.getInstance().execute(r);
    }
}
