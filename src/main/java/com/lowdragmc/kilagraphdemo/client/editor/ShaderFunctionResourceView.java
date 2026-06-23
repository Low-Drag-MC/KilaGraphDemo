package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResourceProviderContainer;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.function.Consumer;

/**
 * A self-contained Shader-Function resource panel — built directly on
 * {@link GraphResourceProviderContainer} instead of LDLib2's heavy {@code Editor}/{@code ResourceView}.
 * Lists the {@link LocalShaderFunctions local} Shader-Function graphs (draggable into a RenderType graph
 * as subgraphs, created via "New", opened for editing in the host window). The base container touches
 * {@code Editor} only for history/centre-window placement; those paths are overridden here.
 */
public class ShaderFunctionResourceView extends View {

    private final LocalShaderFunctions store;
    private final Container container;

    /** @param openInWindow opens an edited Shader-Function's editor view in the host window. */
    public ShaderFunctionResourceView(LocalShaderFunctions store, Consumer<View> openInWindow) {
        this.store = store;
        setName("kilagraphdemo.ui.editor.view_shader_functions");
        getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).heightPercent(100).gapAll(2).paddingAll(2);

        container = new Container(openInWindow, store);
        container.getLayout().flex(1).widthPercent(100);
        container.reloadResourceContainer();

        var actions = new UIElement();
        actions.getLayout().flexDirection(FlexDirection.ROW).widthPercent(100).gapAll(2);
        actions.addChild(new Button().setText("kilagraphdemo.ui.editor.new_shader").setOnClick(e -> container.addNewDefault())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.editor.new_shader.tooltip")));
        actions.addChild(new Button().setText("kilagraphdemo.ui.editor.rename_shader").setOnClick(e -> container.beginRename())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.editor.rename_shader.tooltip")));
        actions.addChild(new Button().setText("kilagraphdemo.ui.editor.delete_shader").setOnClick(e -> confirmDelete())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.editor.delete_shader.tooltip")));
        addChild(actions);
        addChild(container);
    }

    /** Prompt before deleting the selected Shader-Function (an accidental click shouldn't nuke a file). */
    private void confirmDelete() {
        if (container.getSelected() == null) return;
        Dialog.showCheckBox("kilagraphdemo.ui.editor.delete_shader",
                "kilagraphdemo.ui.editor.delete_shader.confirm", result -> {
                    if (result) container.deleteSelected();
                }).show(this);
    }

    /** Seed a new Shader-Function resource from an exported selection's tag (from the graph editor's
     *  "Export as Shader Function" action) and select it. */
    public void addExported(CompoundTag tag) {
        container.addExported(tag);
    }

    /** Resolve an external Shader-Function reference against the local store (for the editor's resolver). */
    public ShaderFunctionGraph resolve(IResourcePath path) {
        return store.resolve(path);
    }

    /**
     * GraphResourceProviderContainer with the Editor-coupled paths (history, centre-window placement)
     * replaced: "New"/edit work without an Editor, opening edited graphs in the host window instead.
     */
    private static class Container extends GraphResourceProviderContainer<ShaderFunctionGraph> {
        Container(Consumer<View> openInWindow, LocalShaderFunctions store) {
            super(ShaderFunctionGraphResource.INSTANCE, store.provider());
            setOnEdit((c, path) -> {
                var tag = store.getRawTag(path);
                if (tag == null) return;
                var graph = ShaderFunctionGraphResource.INSTANCE.createGraph();
                graph.graphModel.deserialize(TagValueInput.create(
                        ProblemReporter.Collector.DISCARDING, Platform.getFrozenRegistry(), tag));
                var view = new GraphEditorView(getGraphViewFactory());
                view.loadGraph(graph, saved -> {
                    store.provider().addResource(path, saved);
                    reloadSpecificResource(path);
                });
                view.setCanRemove(true);
                view.setDynamicName(() -> Component.literal(path.getResourceName()));
                openInWindow.accept(view);
            });
        }

        /** Create a new empty Shader-Function (bypasses the base add path, which needs an Editor's history). */
        void addNewDefault() {
            var graph = ShaderFunctionGraphResource.INSTANCE.createGraph();
            var output = TagValueOutput.createWithContext(
                    ProblemReporter.Collector.DISCARDING, Platform.getFrozenRegistry());
            graph.graphModel.serialize(output);
            var key = resourceProvider.createSubPath("shader_function");
            int n = 1;
            while (resourceProvider.hasResource(key)) {
                key = resourceProvider.createSubPath("shader_function_" + n++);
            }
            resourceProvider.addResource(key, output.buildResult());
            appendResourceUI(key);
            selectResource(key);
        }

        /** Seed a new resource from an already-serialized Shader-Function {@code tag} (an exported selection),
         *  auto-named and selected — mirrors {@link #addNewDefault} but skips creating an empty graph. */
        void addExported(CompoundTag tag) {
            if (tag == null) return;
            var key = resourceProvider.createSubPath("shader_function");
            int n = 1;
            while (resourceProvider.hasResource(key)) {
                key = resourceProvider.createSubPath("shader_function_" + n++);
            }
            resourceProvider.addResource(key, tag);
            appendResourceUI(key);
            selectResource(key);
        }

        /** Delete the selected resource, bypassing the base remove path (which needs an Editor's history).
         *  The {@code FileResourceProvider} deletes the backing file; the list is rebuilt from the provider. */
        void deleteSelected() {
            var key = getSelected();
            if (key == null || !resourceProvider.hasResource(key)) return;
            resourceProvider.removeResource(key);
            selected = null;
            reloadResourceContainer();
        }

        /** Begin inline renaming of the selected resource (the base sets up the text field + uniqueness;
         *  {@link #onRename} commits the rename without an Editor's history). */
        void beginRename() {
            var key = getSelected();
            if (key != null) renameResource(key);
        }

        /** Commit a rename on the {@code FileResourceProvider} directly (the base path needs an Editor's
         *  history): move the resource to the new path and rebuild the list. */
        @Override
        protected void onRename(IResourcePath oldPath, IResourcePath newPath) {
            var value = resourceProvider.getResource(oldPath);
            if (value == null) return;
            resourceProvider.addResource(newPath, value);
            resourceProvider.removeResource(oldPath);
            selected = null;
            reloadResourceContainer();
            selectResource(newPath);
        }
    }
}
