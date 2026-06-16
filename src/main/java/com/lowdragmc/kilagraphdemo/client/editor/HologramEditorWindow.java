package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphEditorView;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.ui.WorldViewPanel;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.lowdragmc.lowdraglib2.editor.ui.SplittableWindow;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/**
 * The hologram graph editor: a plain {@link SplittableWindow} hosting three views — the
 * {@link RenderTypeGraphEditorView} (left) editing the work's graph, a self-built
 * {@link ShaderFunctionResourceView} (top-right) for accessing / using Shader-Function subgraphs, and a
 * {@link ModelSettingsView} (bottom-right) for the display model. No LDLib2 {@code Editor}.
 */
public final class HologramEditorWindow {

    /** Save callback: the serialized graph, the current model, and the bundled Shader-Function deps. */
    public interface SaveCallback {
        void onSave(CompoundTag graphTag, ModelSelection model, Map<String, CompoundTag> resources);
    }

    private HologramEditorWindow() {
    }

    public static UIElement build(HologramDisplay display, ModelSelection initialModel, SaveCallback onSaved) {
        ModelSelection[] model = {initialModel};
        LocalShaderFunctions store = new LocalShaderFunctions();

        SplittableWindow root = new SplittableWindow();
        var split = root.splitStyle(s -> s.percentage(70).minPercentage(20).maxPercentage(88))
                .splitNew(SplittableWindow.Edge.RIGHT);
        SplittableWindow leftWin = split.getFirst();
        SplittableWindow rightWin = split.getSecond();
        var rightSplit = rightWin.splitStyle(s -> s.percentage(50).minPercentage(15).maxPercentage(85))
                .splitNew(SplittableWindow.Edge.BOTTOM);
        SplittableWindow resourceWin = rightSplit.getFirst();
        // Bottom-right splits again into model settings (top) and the live world view (bottom).
        var bottomSplit = rightSplit.getSecond().splitStyle(s -> s.percentage(45).minPercentage(15).maxPercentage(85))
                .splitNew(SplittableWindow.Edge.BOTTOM);
        SplittableWindow modelWin = bottomSplit.getFirst();
        SplittableWindow worldWin = bottomSplit.getSecond();

        ShaderFunctionResourceView resourceView =
                new ShaderFunctionResourceView(store, view -> leftWin.getLeftTop().addView(view));

        // The editor resolves external Shader-Function references against the local store (deps of a
        // downloaded work are written there on download, so they're present for editing too).
        RenderTypeGraph graph = display.graph();
        graph.graphModel.setReferenceResolver(store::resolve);

        RenderTypeGraphEditorView editorView = new RenderTypeGraphEditorView(RenderTypeGraphView::new);
        editorView.loadGraph(graph, tag ->
                onSaved.onSave(tag, model[0], DependencyPacker.collect(graph, store)));

        ModelSettingsView modelView = new ModelSettingsView(display, initialModel, m -> model[0] = m);

        leftWin.getLeftTop().addView(editorView);
        resourceWin.getLeftTop().addView(resourceView);
        modelWin.getLeftTop().addView(modelView);
        worldWin.getLeftTop().addView(new WorldViewPanel());
        return root;
    }
}
