package com.lowdragmc.kilagraphdemo.slideshow;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.Sampler2DValue;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * The SlideShow image input: a {@code SAMPLER2D} source whose texture is supplied <b>externally</b> at
 * render time — SlideShow's decoded slide ({@code GpuTexture}) is bound over this node's placeholder via
 * {@link com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial#setTextureView}. It replaces
 * KilaGraph's {@code TextureNode} in a {@link SlideShowGraph} (which forbids picking an arbitrary texture):
 * the slide is the only texture source. Feed its {@code sampler} output into a {@code SamplerTexture2DNode}.
 *
 * <p>It compiles to an ordinary {@code uniform sampler2D} (reusing {@code ctx.textureSampler}) with a
 * NEAREST/CLAMP placeholder default — so before the slide is bound the material still has a valid binding —
 * and because a {@code SlideShowGraph} has no other texture node, every custom sampler the material manages
 * is one of these and all are rebound to the slide each draw.</p>
 */
@NodeAttribute(name = "ss_sampler0", group = "slideshow_texture", graphTypes = SlideShowGraph.class)
public class Sampler0Node extends ShaderNode {

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("sampler", RenderTypeGraphTypes.SAMPLER2D);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Placeholder default (NEAREST/CLAMP like the slide pipeline's Sampler0); the real slide texture is
        // bound at render time by the SlideShow render hook via material.setTextureView(...).
        ctx.output("sampler", ctx.textureSampler(Sampler2DValue.defaultValue()));
    }

    @Override
    public float getNodeWidth() {
        return 120;
    }
}
