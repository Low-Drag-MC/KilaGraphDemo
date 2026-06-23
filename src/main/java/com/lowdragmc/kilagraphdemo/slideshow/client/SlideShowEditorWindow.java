package com.lowdragmc.kilagraphdemo.slideshow.client;

import com.lowdragmc.kilagraphdemo.client.editor.DependencyPacker;
import com.lowdragmc.kilagraphdemo.client.editor.LocalShaderFunctions;
import com.lowdragmc.kilagraphdemo.client.editor.ShaderFunctionResourceView;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraph;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraphEditorView;
import com.lowdragmc.lowdraglib2.editor.ui.SplittableWindow;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/**
 * The SlideShow graph editor — a lean cousin of {@code HologramEditorWindow}: a {@link SplittableWindow}
 * hosting the {@link SlideShowGraphEditorView} (left) and a {@link ShaderFunctionResourceView} (right) for
 * reusable shader-function subgraphs. There is no model/world view — the slide geometry is the projector's
 * own quad, and the render settings are locked, so the editor is just "graph + its dependencies".
 */
public final class SlideShowEditorWindow {

    /** Save callback: the serialized graph and the bundled Shader-Function dependencies. */
    public interface SaveCallback {
        void onSave(CompoundTag graphTag, Map<String, CompoundTag> resources);
    }

    private SlideShowEditorWindow() {}

    /** Handle to a built editor: root element + dirty/save/exit control (mirrors the hologram editor). */
    public static final class Handle {
        public final UIElement root;
        private final SlideShowGraphEditorView editorView;
        private CompoundTag savedGraphTag;

        private Handle(UIElement root, SlideShowGraphEditorView editorView, CompoundTag savedGraphTag) {
            this.root = root;
            this.editorView = editorView;
            this.savedGraphTag = savedGraphTag;
        }

        public boolean isDirty() {
            return !editorView.serializeGraph().equals(savedGraphTag);
        }

        public void save() {
            editorView.popToLevel(0);   // never save while dived into a subgraph
            editorView.notifySaved();   // → onSaved callback persists + records the snapshot
        }

        private void recordSaved(CompoundTag graphTag) {
            this.savedGraphTag = graphTag;
        }

        /** Add an "exit" button to the graph editor's header that runs {@code onExit} (host wires Esc here). */
        public void installExitButton(Runnable onExit) {
            Button exit = new Button();
            exit.noText().setOnClick(e -> onExit.run());
            exit.getLayout().width(14).heightPercent(100);
            exit.style(s -> s.appendTooltipsString("kilagraphdemo.ui.common.exit.tooltip"));
            UIElement icon = new UIElement().addClass("__white_icon__");
            icon.getLayout().heightPercent(100).setAspectRatio(1);
            icon.style(s -> s.backgroundTexture(Icons.WINDOW_CLOSE));
            exit.addChild(icon);
            editorView.graphView.header.select(".__node-graph-view_header-right__").findFirst()
                    .ifPresent(section -> section.addChild(exit));
        }
    }

    public static Handle build(SlideShowGraph graph, SaveCallback onSaved) {
        Handle[] handleRef = new Handle[1];
        LocalShaderFunctions store = new LocalShaderFunctions();

        SplittableWindow root = new SplittableWindow();
        var split = root.splitStyle(s -> s.percentage(72).minPercentage(20).maxPercentage(88))
                .splitNew(SplittableWindow.Edge.RIGHT);
        SplittableWindow leftWin = split.getFirst();
        SplittableWindow resourceWin = split.getSecond();

        ShaderFunctionResourceView resourceView =
                new ShaderFunctionResourceView(store, view -> leftWin.getLeftTop().addView(view));

        // Resolve external Shader-Function references against the local store (deps of a downloaded work are
        // written there on download, so they're present for editing too).
        graph.graphModel.setReferenceResolver(store::resolve);

        SlideShowGraphEditorView editorView = new SlideShowGraphEditorView();
        editorView.loadGraph(graph, tag -> {
            onSaved.onSave(tag, DependencyPacker.collect(graph, store));
            if (handleRef[0] != null) handleRef[0].recordSaved(tag);
        });

        leftWin.getLeftTop().addView(editorView);
        resourceWin.getLeftTop().addView(resourceView);

        Handle handle = new Handle(root, editorView, editorView.serializeGraph());
        handleRef[0] = handle;
        return handle;
    }
}
