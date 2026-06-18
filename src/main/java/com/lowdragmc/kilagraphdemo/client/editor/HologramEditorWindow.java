package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphEditorView;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.ui.WorldViewPanel;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.lowdragmc.lowdraglib2.editor.ui.SplittableWindow;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/**
 * The hologram graph editor: a plain {@link SplittableWindow} hosting three views — the
 * {@link RenderTypeGraphEditorView} (left) editing the work's graph, a self-built
 * {@link ShaderFunctionResourceView} (top-right) for accessing / using Shader-Function subgraphs, and a
 * {@link ModelSettingsView} (bottom-right) for the display model. No LDLib2 {@code Editor}.
 *
 * <p>{@link #build} returns a {@link Handle} so the hosting screen can query combined dirtiness (graph
 * <i>and</i> model) and trigger a save — the model is part of the work, so its edits must persist and prompt
 * on close just like graph edits.</p>
 */
public final class HologramEditorWindow {

    /** Save callback: the serialized graph, the current model, and the bundled Shader-Function deps. */
    public interface SaveCallback {
        void onSave(CompoundTag graphTag, ModelSelection model, Map<String, CompoundTag> resources);
    }

    private HologramEditorWindow() {
    }

    /**
     * Handle to a built editor: its root element plus combined-dirty/save control. Dirtiness compares the live
     * graph + model against the last-saved snapshot; {@link #save()} routes through the graph editor's root
     * save (which persists graph + model together via {@code onSaved}).
     */
    public static final class Handle {
        public final UIElement root;
        private final RenderTypeGraphEditorView editorView;
        private final ModelSelection[] model; // [0] = current model (updated live by ModelSettingsView)
        private CompoundTag savedGraphTag;
        private ModelSelection savedModel;

        private Handle(UIElement root, RenderTypeGraphEditorView editorView, ModelSelection[] model,
                       CompoundTag savedGraphTag, ModelSelection savedModel) {
            this.root = root;
            this.editorView = editorView;
            this.model = model;
            this.savedGraphTag = savedGraphTag;
            this.savedModel = savedModel;
        }

        /** True if the graph or the model differs from the last-saved state. */
        public boolean isDirty() {
            return !editorView.serializeGraph().equals(savedGraphTag) || !model[0].equals(savedModel);
        }

        /** Persist graph + model (at the root level), updating the saved snapshot. */
        public void save() {
            editorView.popToLevel(0);   // never save while dived into a subgraph
            editorView.notifySaved();   // → onSaved callback persists + records the snapshot
        }

        /** Called from the {@code onSaved} callback so a save via either the graph's Save button or {@link #save()} updates the snapshot. */
        private void recordSaved(CompoundTag graphTag) {
            this.savedGraphTag = graphTag;
            this.savedModel = model[0];
        }

        /**
         * Add an "exit" button to the graph editor's header (right section), running {@code onExit} on click —
         * the host screen wires this to the same close path as Esc (prompt-on-unsaved). Lives on the root graph
         * view's header (Esc still covers subgraph-dive views).
         */
        public void installExitButton(Runnable onExit) {
            Button exit = new Button();
            exit.noText().setOnClick(e -> onExit.run());
            exit.getLayout().width(14).heightPercent(100);
            exit.style(s -> s.appendTooltipsString("Exit (Esc)"));
            UIElement icon = new UIElement().addClass("__white_icon__");
            icon.getLayout().heightPercent(100).setAspectRatio(1);
            icon.style(s -> s.backgroundTexture(Icons.WINDOW_CLOSE));
            exit.addChild(icon);
            editorView.graphView.header.select(".__node-graph-view_header-right__").findFirst()
                    .ifPresent(section -> section.addChild(exit));
        }
    }

    public static Handle build(GlobalPos blockPos, HologramDisplay display,
                               ModelSelection initialModel, SaveCallback onSaved) {
        ModelSelection[] model = {initialModel};
        Handle[] handleRef = new Handle[1];
        LocalShaderFunctions store = new LocalShaderFunctions();

        SplittableWindow root = new SplittableWindow();
        var split = root.splitStyle(s -> s.percentage(70).minPercentage(20).maxPercentage(88))
                .splitNew(SplittableWindow.Edge.RIGHT);
        SplittableWindow leftWin = split.getFirst();
        SplittableWindow rightWin = split.getSecond();
        var rightSplit = rightWin.splitStyle(s -> s.percentage(30).minPercentage(15).maxPercentage(85))
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
        editorView.loadGraph(graph, tag -> {
            onSaved.onSave(tag, model[0], DependencyPacker.collect(graph, store));
            if (handleRef[0] != null) handleRef[0].recordSaved(tag);
        });

        // Model edits update the live display + current model, and mark the editor dirty so the work's Save
        // (and the close prompt) covers model changes — not only graph changes.
        ModelSettingsView modelView = new ModelSettingsView(blockPos, display, initialModel, m -> {
            model[0] = m;
            editorView.markAsDirty();
        });

        leftWin.getLeftTop().addView(editorView);
        resourceWin.getLeftTop().addView(resourceView);
        modelWin.getLeftTop().addView(modelView);
        worldWin.getLeftTop().addView(new WorldViewPanel());

        Handle handle = new Handle(root, editorView, model, editorView.serializeGraph(), initialModel);
        handleRef[0] = handle;
        return handle;
    }
}
