package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.editor.ExportShaderFunction;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.function.Consumer;

/**
 * Demo variant of {@link RenderTypeGraphView} whose "Export as Shader Function" right-click action
 * routes into the demo's {@link ShaderFunctionResourceView} instead of an LDLib2 {@code Editor}'s
 * Resources panel. The base action navigates {@code getFirstAncestorOfType(Editor.class)} → the
 * editor's resource view, but the hologram editor uses no {@code Editor} (a plain
 * {@code SplittableWindow} + custom views), so the base action silently no-ops. Here we drop the base
 * leaf and add a working one that builds the function tag (reusing the public {@link ExportShaderFunction})
 * and hands it to the {@code exportTarget}.
 */
public class DemoRenderTypeGraphView extends RenderTypeGraphView {

    /** Receives the serialized Shader-Function tag of an exported selection. */
    private final Consumer<CompoundTag> exportTarget;

    public DemoRenderTypeGraphView(Consumer<CompoundTag> exportTarget) {
        this.exportTarget = exportTarget;
    }

    @Override
    protected TreeBuilder.Menu createMenu(float mouseX, float mouseY) {
        var menu = super.createMenu(mouseX, mouseY);
        // Mirror the base guards: the export leaf is only meaningful with a node selection and when the
        // cursor isn't over a NodeShaderPreview (which gets its own geometry-picker menu instead).
        if (findHoveredPreview() == null && !selectedElementModels().isEmpty()) {
            // Drop the base (Editor-bound, no-op here) leaf and replace it with one wired to the demo store.
            menu.remove("rendertypegraph.export_shader_function");
            menu.leaf("rendertypegraph.export_shader_function", this::exportToStore);
        }
        return menu;
    }

    /** Build a standalone Shader-Function from the current selection and hand its tag to the store. */
    private void exportToStore() {
        Graph graph = getGraph();
        if (graph == null) return;
        var fn = ExportShaderFunction.build(graph.graphModel, selectedElementModels(), Platform.getFrozenRegistry());
        if (fn == null) return;
        exportTarget.accept(ExportShaderFunction.serialize(fn));
    }

    /** Replicates {@code RenderTypeGraphView.selectedElementModels} (private there): the selected elements
     *  that are copiable graph elements (nodes), the input {@link ExportShaderFunction#build} expects. */
    private List<GraphElementModel> selectedElementModels() {
        return getSelected().stream()
                .filter(GraphElementModel.class::isInstance)
                .map(GraphElementModel.class::cast)
                .toList();
    }
}
