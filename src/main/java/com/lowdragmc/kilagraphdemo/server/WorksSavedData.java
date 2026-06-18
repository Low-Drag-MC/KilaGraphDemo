package com.lowdragmc.kilagraphdemo.server;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.graph.ServerWorkEntry;
import com.lowdragmc.kilagraphdemo.graph.WorkMeta;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world store of shared works. The server keeps only <b>metadata</b> ({@link WorkMeta}) and
 * <b>likes</b> (in {@code index.nbt}) plus each work's opaque payload bytes (one file per uid). It never
 * parses the graph — it only reads the package's {@code meta} sub-tag. Persistence is managed here via
 * our own files under the world's {@code data/} dir (the {@link SavedData} is just a per-world holder).
 */
public class WorksSavedData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SavedDataType<WorksSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "works"),
            WorksSavedData::new,
            serverLevel -> MapCodec.unitCodec(() -> new WorksSavedData(serverLevel)),
            DataFixTypes.LEVEL);

    /** Outcome of an upload attempt. */
    public enum UploadStatus { PUBLISHED, UPDATED, REJECTED_LIMIT }

    public record UploadResult(UploadStatus status, @Nullable WorkMeta meta) {}

    private final Path dir;
    private final Map<String, WorkMeta> metas = new HashMap<>();
    private final Map<String, Set<UUID>> likes = new HashMap<>();
    /** authorUuid -> their published uids; rebuilt from {@link #metas}. Capped by {@link Kilagraphdemo#MAX_WORKS_PER_PLAYER}. */
    private final Map<UUID, Set<String>> authorIndex = new HashMap<>();

    private WorksSavedData(ServerLevel level) {
        this.dir = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve(Kilagraphdemo.MODID + "_works");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to create works dir {}", dir, e);
        }
        loadIndex();
    }

    public static WorksSavedData get(ServerLevel level) {
        // Stored on the overworld so all dimensions share one library.
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- queries -----------------------------------------------------------------------------

    public List<ServerWorkEntry> listEntries(UUID requester) {
        List<ServerWorkEntry> out = new ArrayList<>();
        for (WorkMeta meta : metas.values()) {
            Set<UUID> likers = likes.getOrDefault(meta.uid(), Set.of());
            out.add(new ServerWorkEntry(meta, likers.size(), likers.contains(requester)));
        }
        return out;
    }

    /** Whether a work with this uid is published (used to validate a server-hologram display selection). */
    public boolean exists(String uid) {
        return metas.containsKey(uid);
    }

    /** How many works {@code author} currently has published. */
    private int countFor(UUID author) {
        return authorIndex.getOrDefault(author, Set.of()).size();
    }

    @Nullable
    public CompoundTag getPayload(String uid) {
        Path file = payloadFile(uid);
        if (!Files.exists(file)) return null;
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to read payload {}", uid, e);
            return null;
        }
    }

    // ---- mutations ---------------------------------------------------------------------------

    /**
     * Publish or update a work. Updating one of the author's own existing works is always allowed; publishing
     * a <em>new</em> work is rejected once the author already has {@link Kilagraphdemo#MAX_WORKS_PER_PLAYER}
     * works, unless they {@link Kilagraphdemo#canBypassUploadLimit can bypass} (Creative/Op).
     */
    public UploadResult upsert(ServerPlayer player, CompoundTag packageTag) {
        WorkMeta incoming = WorkMeta.fromTag(packageTag.getCompoundOrEmpty("meta"));
        String uid = incoming.uid();
        UUID author = player.getUUID();
        String authorStr = author.toString();
        long now = System.currentTimeMillis();

        WorkMeta existing = metas.get(uid);
        boolean ownedUpdate = existing != null && existing.authorUuid().equals(authorStr);

        WorkMeta authoritative;
        UploadStatus status;
        if (ownedUpdate) {
            authoritative = new WorkMeta(uid, existing.version() + 1, authorStr, player.getName().getString(),
                    incoming.title(), incoming.description(), existing.firstUploadTime(), now);
            status = UploadStatus.UPDATED;
        } else {
            if (!Kilagraphdemo.canBypassUploadLimit(player) && countFor(author) >= Kilagraphdemo.MAX_WORKS_PER_PLAYER) {
                return new UploadResult(UploadStatus.REJECTED_LIMIT, null);
            }
            // Defensive: never overwrite a work already owned by someone else (e.g. an uploaded fork
            // that kept the original uid). Mint a fresh uid so the original author's work is untouched.
            if (metas.containsKey(uid)) {
                uid = UUID.randomUUID().toString();
            }
            authoritative = new WorkMeta(uid, 1, authorStr, player.getName().getString(),
                    incoming.title(), incoming.description(), now, now);
            authorIndex.computeIfAbsent(author, k -> new HashSet<>()).add(uid);
            likes.computeIfAbsent(uid, k -> new HashSet<>());
            status = UploadStatus.PUBLISHED;
        }
        metas.put(uid, authoritative);

        // Store the package, but with the server-authoritative meta baked in so downloads carry it.
        CompoundTag toStore = packageTag.copy();
        toStore.put("meta", authoritative.toTag());
        writePayload(uid, toStore);
        saveIndex();
        return new UploadResult(status, authoritative);
    }

    /** Delete a work — only its author may. Returns whether anything was removed. */
    public boolean delete(String uid, ServerPlayer player) {
        WorkMeta meta = metas.get(uid);
        if (meta == null || !meta.authorUuid().equals(player.getUUID().toString())) return false;
        metas.remove(uid);
        likes.remove(uid);
        Set<String> owned = authorIndex.get(player.getUUID());
        if (owned != null) {
            owned.remove(uid);
            if (owned.isEmpty()) authorIndex.remove(player.getUUID());
        }
        try {
            Files.deleteIfExists(payloadFile(uid));
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to delete payload {}", uid, e);
        }
        saveIndex();
        return true;
    }

    /** One like per (player, uid). */
    public void setLike(String uid, UUID who, boolean like) {
        if (!metas.containsKey(uid)) return;
        Set<UUID> set = likes.computeIfAbsent(uid, k -> new HashSet<>());
        if (like) {
            set.add(who);
        } else {
            set.remove(who);
        }
        saveIndex();
    }

    // ---- persistence (our own files) ---------------------------------------------------------

    private Path payloadFile(String uid) {
        return dir.resolve(uid + ".nbt");
    }

    private void writePayload(String uid, CompoundTag tag) {
        try {
            NbtIo.writeCompressed(tag, payloadFile(uid));
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to write payload {}", uid, e);
        }
    }

    private void loadIndex() {
        Path file = dir.resolve("index.nbt");
        if (!Files.exists(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            for (Tag t : root.getListOrEmpty("works")) {
                if (t instanceof CompoundTag mt) {
                    WorkMeta meta = WorkMeta.fromTag(mt);
                    metas.put(meta.uid(), meta);
                    try {
                        authorIndex.computeIfAbsent(UUID.fromString(meta.authorUuid()), k -> new HashSet<>())
                                .add(meta.uid());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            CompoundTag likesTag = root.getCompoundOrEmpty("likes");
            for (String uid : likesTag.keySet()) {
                Set<UUID> set = new HashSet<>();
                for (Tag t : likesTag.getListOrEmpty(uid)) {
                    try {
                        set.add(UUID.fromString(t.asString().orElse("")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                likes.put(uid, set);
            }
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to load works index", e);
        }
    }

    private void saveIndex() {
        CompoundTag root = new CompoundTag();
        ListTag works = new ListTag();
        metas.values().forEach(m -> works.add(m.toTag()));
        root.put("works", works);
        CompoundTag likesTag = new CompoundTag();
        likes.forEach((uid, set) -> {
            ListTag list = new ListTag();
            set.forEach(u -> list.add(StringTag.valueOf(u.toString())));
            likesTag.put(uid, list);
        });
        root.put("likes", likesTag);
        try {
            NbtIo.writeCompressed(root, dir.resolve("index.nbt"));
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to save works index", e);
        }
    }
}
