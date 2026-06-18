package com.lowdragmc.kilagraphdemo.slideshow;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets;
import com.lowdragmc.kilagraph.rendertype.nodes.input.VertexColorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link RenderTypeGraph} specialized for driving SlideShow projector rendering. Its render settings are
 * <b>locked</b> to match SlideShow's {@code SLIDE_PIPELINE} (a {@code BLOCK_SNIPPET}-based pipeline:
 * {@code DefaultVertexFormat.BLOCK}, {@code QUADS}, {@code TRANSLUCENT} blend, lightmap on) so the
 * RenderType we substitute is compatible with the geometry SlideShow submits. The palette forbids the
 * generic {@link TextureNode} and offers {@link Sampler0Node} instead — the slide image is the only texture
 * source, fed in externally at render time.
 *
 * <p>This class references only KilaGraph (never {@code org.teacon.slides}), so it is safe to load even when
 * the SlideShow mod is absent; only the render/UI hooks are gated on SlideShow's presence.</p>
 */
public class SlideShowGraph extends RenderTypeGraph {

    public SlideShowGraph() {
        this(true);
    }

    public SlideShowGraph(boolean initialize) {
        super(initialize);
        // Always force the slide-compatible settings, on both fresh-create and deserialize paths (the
        // resource doesn't persist Settings for a SlideShowGraph — they're implicit).
        applyLockedSettings();
    }

    /** Settings forced to match SlideShow's {@code SLIDE_PIPELINE}. */
    public static Settings lockedSettings() {
        return new Settings(
                VertexFormatPresets.BLOCK,
                Settings.VertexFormatMode.QUADS,
                Settings.BlendMode.TRANSLUCENT,
                Settings.DepthTest.LEQUAL,
                true,   // depthWrite (alpha-cutout slides write depth)
                false,  // cull (slides may be viewed from either side)
                Settings.OutputTarget.MAIN,
                false,  // affectsOutline
                true    // sortOnUpload
        );
    }

    private void applyLockedSettings() {
        super.setSettings(lockedSettings());
    }

    /** Render settings are locked for SlideShow compatibility — ignore external edits, always re-apply. */
    @Override
    public void setSettings(Settings settings) {
        super.setSettings(lockedSettings());
    }

    /** The slide image enters through {@link Sampler0Node}, not the generic KilaGraph TextureNode. */
    @Override
    protected NodeModel createDefaultTextureSource() {
        return createNode(Sampler0Node.class, 48, -128);
    }

    /** A slide quad has no usable Normal, so the default vertex colour uses the baked lightmap (block.vsh
     *  style: {@code Color * sample_lightmap(Sampler2, UV2)}) rather than per-vertex diffuse. */
    @Override
    protected String defaultVertexColorMode() {
        return VertexColorNode.MODE_BLOCK;
    }

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        // Reuse the RenderType node set, but swap TextureNode -> Sampler0Node. getSupportNodes() drives both
        // the editor palette and node (de)serialization (findNodeByClassName), so this is the single switch.
        var list = new ArrayList<>(RenderTypeGraph.NODE_REGISTRY.getNodeClasses());
        list.remove(TextureNode.class);
        if (!list.contains(Sampler0Node.class)) {
            list.add(Sampler0Node.class);
        }
        return List.copyOf(list);
    }
}
