package com.lowdragmc.kilagraphdemo.graph.node;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.Sampler2DValue;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * A {@link TextureNode} variant whose texture is a <b>local PNG file</b> picked from the mod's assets dir
 * ({@code <gameDir>/ldlib2/assets/kilagraphdemo/textures}). It reuses {@code SAMPLER2D}/{@link Sampler2DValue}
 * and the exact same compile/binding as {@code TextureNode} (so the runtime path is unchanged); only the
 * editor configurator differs — a file picker that fills in a {@code kilagraphdemo:textures/…} location
 * instead of a free-form id. Such locations are bundled with the graph on upload (see {@code TextureBundler})
 * so shared works render their textures on every client.
 *
 * <p>Headless-safe: the client-only configurator is referenced only inside the {@code withConfigurable}
 * lambda (lazily, like KilaGraph's {@code Sampler2DConfigurator}), so the compiler/server path never loads UI.</p>
 */
@NodeAttribute(name = "rt_file_texture", group = "rendertype_texture",
        graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FileTextureNode extends TextureNode {

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("texture", RenderTypeGraphTypes.SAMPLER2D)
                .withDisplayName(Component.empty())
                .withDefaultValue(Sampler2DValue.defaultValue())
                .withConfigurable((valueConfigurable, typeHandle) ->
                        com.lowdragmc.kilagraphdemo.client.editor.FileTextureConfigurator.build(valueConfigurable))
                .build();
    }
}
