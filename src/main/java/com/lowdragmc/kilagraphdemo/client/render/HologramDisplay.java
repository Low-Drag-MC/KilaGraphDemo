package com.lowdragmc.kilagraphdemo.client.render;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A client-side "what a hologram is showing": a live {@link RenderTypeGraph} plus the geometry
 * ({@link KGPreviewContent}) it's drawn on. Owns the compiled {@link RenderTypeGraphMaterial} and
 * rebuilds it only when the graph changes — so editing the graph live updates every hologram bound to
 * this same instance, and identical displays (e.g. the shared default) reuse one material across blocks.
 *
 * <p>Mirrors {@code com.lowdragmc.kilagraph.rendertype.preview.NodeShaderPreview#updateMaterial}.</p>
 */
public class HologramDisplay {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RenderTypeGraph graph;
    private KGPreviewContent content;
    /** The work's cull radius (block units) — drives the renderer's expanded render bounding box. */
    private final float renderRadius;

    @Nullable
    private RenderTypeGraphMaterial material;
    private long lastChangeVersion = Long.MIN_VALUE;
    private boolean lastBuildFailed = false;

    public HologramDisplay(RenderTypeGraph graph, @Nullable KGPreviewContent content) {
        this(graph, content, com.lowdragmc.kilagraphdemo.graph.ModelSelection.DEFAULT_RADIUS);
    }

    public HologramDisplay(RenderTypeGraph graph, @Nullable KGPreviewContent content, float renderRadius) {
        this.graph = graph;
        this.content = content != null ? content : KGPreviewContents.CUBE;
        this.renderRadius = renderRadius;
    }

    public RenderTypeGraph graph() {
        return graph;
    }

    public float renderRadius() {
        return renderRadius;
    }

    public KGPreviewContent content() {
        return content;
    }

    public void setContent(KGPreviewContent content) {
        if (content != null) this.content = content;
    }

    /**
     * Returns the current material, recompiling only when the graph's change-version moved (an edit).
     * Returns {@code null} while the graph fails to compile (the caller then skips the model). Must run
     * on the render thread; a stray off-thread call is swallowed (no material this frame) rather than
     * crashing.
     */
    @Nullable
    public RenderTypeGraphMaterial updateMaterial() {
        long version = graph.getChangeVersion();
        if (material != null && version == lastChangeVersion) return material;
        lastChangeVersion = version;

        RenderTypeGraphMaterial rebuilt;
        try {
            rebuilt = RenderTypeFactory.createMaterial(graph);
        } catch (RuntimeException e) {
            if (!lastBuildFailed) {
                LOGGER.warn("[KilaGraphDemo] hologram material build failed: {}", e.getMessage());
                lastBuildFailed = true;
            }
            return material;
        }
        if (rebuilt == null) {
            lastBuildFailed = true;
            return material;
        }
        lastBuildFailed = false;
        if (material != null) material.close();
        material = rebuilt;
        return material;
    }

    public void close() {
        if (material != null) {
            material.close();
            material = null;
        }
        lastChangeVersion = Long.MIN_VALUE;
    }
}
