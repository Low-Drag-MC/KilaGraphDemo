package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.lowdragmc.kilagraphdemo.graph.WorkPackage;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

/**
 * Rewrites a work for upload so a custom OBJ model travels with it. Far simpler than {@link TextureBundler}:
 * a work has exactly one {@link ModelSelection}, so there's no graph to walk. If the selection is a custom OBJ
 * referencing a local {@code kilagraphdemo:…} file, this reads the bytes, rewrites the location to
 * {@code kilagraphdemo:downloaded/<uid>/model.obj}, and returns a new {@link WorkPackage} carrying the rewritten
 * selection plus the raw OBJ bytes (in {@link WorkPackage#models()}). Primitive/empty selections pass through
 * unchanged. Uploads exceeding {@link #MAX_MODEL_BYTES} are rejected.
 *
 * <p>Client-only (reads files under {@link Kilagraphdemo#getAssetsDir()}).</p>
 */
public final class ModelBundler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Max size of a bundled OBJ model per work (2 MiB), independent of the texture cap. */
    public static final long MAX_MODEL_BYTES = 2L * 1024 * 1024;

    private ModelBundler() {
    }

    /** Outcome: {@code pkg} is the upload-ready package, or {@code tooLarge} when over the size cap. */
    public record Result(@Nullable WorkPackage pkg, boolean tooLarge, long totalBytes) {
    }

    public static Result rewriteForUpload(WorkPackage local, String uploadUid) {
        ModelSelection model = local.model();
        if (!model.isObj()) {
            return new Result(local, false, 0);
        }
        Identifier id = Identifier.tryParse(model.location());
        if (id == null || !id.getNamespace().equals(Kilagraphdemo.MODID)) {
            return new Result(local, false, 0); // not ours (or already a bundled location) — leave as-is
        }
        File file = new File(Kilagraphdemo.getAssetsDir(), id.getPath());
        if (!file.isFile()) {
            LOGGER.warn("[KilaGraphDemo] OBJ model not found for upload: {} ({})",
                    model.location(), file.getAbsolutePath());
            return new Result(local, false, 0);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception ex) {
            LOGGER.error("[KilaGraphDemo] failed to read OBJ model {}", model.location(), ex);
            return new Result(local, false, 0);
        }
        if (bytes.length > MAX_MODEL_BYTES) {
            return new Result(null, true, bytes.length);
        }

        String newLocation = Kilagraphdemo.MODID + ":downloaded/" + uploadUid + "/model.obj";
        // Keep flipV + transform; only the location changes to the bundled copy.
        WorkPackage pkg = local.withModel(model.withLocation(newLocation), Map.of(newLocation, bytes));
        return new Result(pkg, false, bytes.length);
    }
}
