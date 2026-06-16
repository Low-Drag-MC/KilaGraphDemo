package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects the external Shader-Function resources a graph depends on, so a work can be uploaded /
 * shared self-contained. Walks every external {@link SubgraphNodeModel} reference (recursively, since
 * shader functions can nest other shader functions) and pulls each referenced resource's raw tag from
 * the local store, keyed by its {@link IResourcePath#getPathWithType() path string}.
 */
public final class DependencyPacker {

    private DependencyPacker() {
    }

    public static Map<String, CompoundTag> collect(Graph graph, LocalShaderFunctions store) {
        Map<String, CompoundTag> out = new HashMap<>();
        collectFrom(graph, store, out);
        return out;
    }

    private static void collectFrom(Graph graph, LocalShaderFunctions store, Map<String, CompoundTag> out) {
        for (INode node : graph.getNodes()) {
            if (!(node instanceof SubgraphNodeModel sub) || sub.getKind() != SubgraphNodeModel.Kind.EXTERNAL) {
                continue;
            }
            IResourcePath path = sub.getExternalPath();
            if (path == null) continue;
            String key = path.getPathWithType();
            if (out.containsKey(key)) continue;
            CompoundTag tag = store.getRawTag(path);
            if (tag == null) continue;
            out.put(key, tag);
            // Recurse into the referenced function for its own external dependencies.
            ShaderFunctionGraph nested = store.deserialize(tag);
            if (nested != null) collectFrom(nested, store, out);
        }
    }
}
