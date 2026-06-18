package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.editor.RenderTypeGraphResource;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.Sampler2DValue;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.graph.WorkPackage;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rewrites a work for upload so the local PNG textures it references travel with it. Walks the main graph
 * and every bundled shader-function dependency for {@code SAMPLER2D} options whose location is a local
 * {@code kilagraphdemo:…} file, copies each (deduped) under {@code kilagraphdemo:downloaded/<uid>/<index>.png},
 * rewrites the option locations to match, and returns a new {@link WorkPackage} carrying both the rewritten
 * graph/dependencies and the raw PNG bytes. Textures in other namespaces (minecraft, other mods) — which
 * every client already has — are left untouched. Uploads exceeding {@link #MAX_TOTAL_BYTES} are rejected.
 *
 * <p>Client-only (reads files under {@link Kilagraphdemo#getAssetsDir()}).</p>
 */
public final class TextureBundler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Max combined size of bundled PNG bytes per work (8 MiB). */
    public static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;

    private TextureBundler() {
    }

    /** Outcome: {@code pkg} is the upload-ready package, or {@code tooLarge} when over the size cap. */
    public record Result(@Nullable WorkPackage pkg, boolean tooLarge, long totalBytes) {
    }

    public static Result rewriteForUpload(WorkPackage local, String uploadUid, LocalShaderFunctions sfStore) {
        Assigner assigner = new Assigner(uploadUid);

        // Main graph.
        RenderTypeGraph graph = local.loadGraph();
        // --- diagnostics: is FileTextureNode registered, and does the SAVED tag even contain it? ---
        try {
            var support = graph.getSupportNodes().stream().map(Class::getName)
                    .filter(n -> n.toLowerCase().contains("texture")).toList();
            LOGGER.info("[KGD-TexBundle] support texture-nodes={}", support);
            String snbt = local.toTag().toString();
            LOGGER.info("[KGD-TexBundle] savedTag len={} hasFileTextureNode={} hasFileTextureStr={} pngRefs(contains '.png')={}",
                    snbt.length(), snbt.contains("FileTextureNode"),
                    snbt.contains("file_texture"), snbt.contains(".png"));
        } catch (Exception ex) {
            LOGGER.warn("[KGD-TexBundle] diagnostics failed", ex);
        }
        rewriteGraph(graph.getNodes(), assigner);

        // Shader-function dependencies (a FileTextureNode can live inside one too).
        Map<String, CompoundTag> newResources = new HashMap<>();
        for (Map.Entry<String, CompoundTag> e : local.resources().entrySet()) {
            ShaderFunctionGraph sf = sfStore.deserialize(e.getValue());
            if (sf == null) {
                newResources.put(e.getKey(), e.getValue());
                continue;
            }
            rewriteGraph(sf.getNodes(), assigner);
            newResources.put(e.getKey(), sfStore.serialize(sf));
        }

        if (assigner.totalBytes > MAX_TOTAL_BYTES) {
            return new Result(null, true, assigner.totalBytes);
        }

        CompoundTag newGraphTag = RenderTypeGraphResource.INSTANCE.serializeGraph(graph);
        WorkPackage pkg = new WorkPackage(local.meta(), newGraphTag, local.model(), newResources, assigner.bundled);
        LOGGER.info("[KGD-TexBundle] summary: nodesSeen={} samplerOptions={} bundled={} bytes={} (assetsDir={})",
                assigner.nodesSeen, assigner.samplerOptionsSeen, assigner.bundled.size(), assigner.totalBytes,
                Kilagraphdemo.getAssetsDir().getAbsolutePath());
        return new Result(pkg, false, assigner.totalBytes);
    }

    /** Rewrite every {@link Sampler2DValue} option in the given nodes via {@code assigner}. */
    private static void rewriteGraph(@Nullable Collection<? extends INode> nodes, Assigner assigner) {
        if (nodes == null) {
            LOGGER.info("[KGD-TexBundle] rewriteGraph: nodes==null");
            return;
        }
        for (INode node : nodes) {
            assigner.nodesSeen++;
            if (node == null) {
                LOGGER.info("[KGD-TexBundle] null node (unresolved class)");
                continue;
            }
            Collection<? extends INodeOption> options = node.getNodeOptions();
            int optCount = options == null ? -1 : options.size();
            LOGGER.info("[KGD-TexBundle] node {} options={}", node.getClass().getName(), optCount);
            if (options == null) continue;
            for (INodeOption option : options) {
                if (!(option instanceof NodeOption nodeOption)) {
                    LOGGER.info("[KGD-TexBundle]   option not NodeOption: {}", option.getClass().getSimpleName());
                    continue;
                }
                PortModel port = nodeOption.getPortModel();
                Object raw = port.getValue();
                LOGGER.info("[KGD-TexBundle]   option {} value={}", nodeOption.getId(),
                        raw == null ? "null" : raw.getClass().getSimpleName() + "(" + raw + ")");
                if (!(raw instanceof Sampler2DValue value)) continue;
                assigner.samplerOptionsSeen++;
                String newLocation = assigner.assign(value.location());
                if (newLocation != null && !newLocation.equals(value.location())) {
                    port.setValue(value.withLocation(newLocation));
                }
            }
        }
    }

    /** Assigns deduped {@code downloaded/<uid>/<index>.png} locations and collects the bytes. */
    private static final class Assigner {
        private final String uploadUid;
        private final Map<String, String> remap = new HashMap<>();           // original location -> new location
        private final Map<String, byte[]> bundled = new LinkedHashMap<>();    // new location -> bytes
        private long totalBytes;
        private int index;
        int nodesSeen;
        int samplerOptionsSeen;

        Assigner(String uploadUid) {
            this.uploadUid = uploadUid;
        }

        /** New location for a local kilagraphdemo texture, or {@code null} to leave the location unchanged. */
        @Nullable
        String assign(String location) {
            if (location == null || location.isEmpty()) return null;
            String existing = remap.get(location);
            if (existing != null) return existing;
            Identifier id = Identifier.tryParse(location);
            if (id == null || !id.getNamespace().equals(Kilagraphdemo.MODID)) {
                LOGGER.info("[KGD-TexBundle]   assign skip (not kilagraphdemo namespace): {}", location);
                return null; // not ours — leave as-is
            }
            File file = new File(Kilagraphdemo.getAssetsDir(), id.getPath());
            if (!file.isFile()) {
                LOGGER.warn("[KGD-TexBundle]   assign skip (file not found): {} -> {}", location, file.getAbsolutePath());
                return null;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file.toPath());
            } catch (IOException ex) {
                LOGGER.error("[KilaGraphDemo] failed to read texture {}", location, ex);
                return null;
            }
            String newLocation = Kilagraphdemo.MODID + ":downloaded/" + uploadUid + "/" + (index++) + ".png";
            remap.put(location, newLocation);
            bundled.put(newLocation, bytes);
            totalBytes += bytes.length;
            return newLocation;
        }
    }
}
