package com.lowdragmc.kilagraphdemo.slideshow;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.IGraphReferenceResolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Serialize/deserialize helper for {@link SlideShowGraph}. Mirrors KilaGraph's {@code RenderTypeGraphResource}
 * but stores <b>only the node model</b> — a SlideShowGraph's render Settings are locked (implicit), so they
 * are never persisted and are re-applied by the graph itself on load.
 */
public final class SlideShowGraphResource {

    public static final SlideShowGraphResource INSTANCE = new SlideShowGraphResource();
    private static final String GRAPH_TAG = "graph";

    private SlideShowGraphResource() {}

    public CompoundTag serializeGraph(SlideShowGraph graph) {
        var root = new CompoundTag();
        var output = TagValueOutput.createWithContext(ProblemReporter.Collector.DISCARDING, Platform.getFrozenRegistry());
        graph.graphModel.serialize(output);
        root.put(GRAPH_TAG, output.buildResult());
        return root;
    }

    public SlideShowGraph deserializeGraph(CompoundTag tag) {
        return deserializeGraph(tag, null);
    }

    public SlideShowGraph deserializeGraph(CompoundTag tag, @Nullable IGraphReferenceResolver resolver) {
        var graph = new SlideShowGraph(false);
        graph.graphModel.setReferenceResolver(resolver);
        var graphTag = tag.get(GRAPH_TAG) instanceof CompoundTag compound ? compound : tag;
        graph.graphModel.deserialize(TagValueInput.create(ProblemReporter.Collector.DISCARDING,
                Platform.getFrozenRegistry(), graphTag));
        graph.graphModel.setReferenceResolver(resolver);
        graph.restoreFixedStagesAfterDeserialize();
        return graph;
    }
}
