package com.lowdragmc.kilagraphdemo.graph;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Client-side local cache of work packages, one {@code <uid>.nbt} file per work under
 * {@code <gameDir>/kilagraphdemo/graphs/}. Holds both locally-authored works and works downloaded
 * from a server. Client-only — never touch from server code.
 */
public final class LocalGraphStore {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LocalGraphStore() {
    }

    private static Path dir() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("kilagraphdemo").resolve("graphs");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to create local graph dir {}", dir, e);
        }
        return dir;
    }

    private static Path file(String uid) {
        return dir().resolve(uid + ".nbt");
    }

    public static void save(WorkPackage pkg) {
        try {
            NbtIo.writeCompressed(pkg.toTag(), file(pkg.meta().uid()));
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to save work {}", pkg.meta().uid(), e);
        }
    }

    public static Optional<WorkPackage> load(String uid) {
        Path file = file(uid);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(WorkPackage.fromTag(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())));
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to load work {}", uid, e);
            return Optional.empty();
        }
    }

    public static void delete(String uid) {
        try {
            Files.deleteIfExists(file(uid));
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to delete work {}", uid, e);
        }
        // Also remove this work's bundled textures (downloaded works keep them under downloaded/<uid>/),
        // so deleting a work reclaims its texture files too. No-op for a self-authored work (no such folder).
        deleteRecursively(new File(Kilagraphdemo.getAssetsDir(), "downloaded/" + uid));
    }

    private static void deleteRecursively(File path) {
        if (!path.exists()) return;
        File[] children = path.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!path.delete()) {
            LOGGER.warn("[KilaGraphDemo] failed to delete {}", path);
        }
    }

    /** Metadata of every locally-cached work (loads each file; fine for the small counts here). */
    public static List<WorkMeta> list() {
        List<WorkMeta> out = new ArrayList<>();
        Path dir = dir();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".nbt")).forEach(p -> {
                try {
                    var tag = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
                    out.add(WorkMeta.fromTag(tag.getCompoundOrEmpty("meta")));
                } catch (IOException e) {
                    LOGGER.warn("[KilaGraphDemo] skipping unreadable work file {}", p, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to list local works", e);
        }
        return out;
    }
}
