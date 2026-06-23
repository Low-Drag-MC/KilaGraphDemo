package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
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

        addChild(new Button().setText("kilagraphdemo.ui.editor.new_shader").setOnClick(e -> container.addNewDefault())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.editor.new_shader.tooltip")));
        addChild(container);
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
    }
}
