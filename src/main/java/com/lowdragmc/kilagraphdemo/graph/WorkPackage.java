package com.lowdragmc.kilagraphdemo.graph;

import com.lowdragmc.kilagraph.editor.RenderTypeGraphResource;
import com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.IGraphReferenceResolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.HashMap;
import java.util.Map;

/**
 * The self-contained unit stored locally and uploaded: metadata + the payload to reproduce the display —
 * the serialized {@link RenderTypeGraph}, the {@link ModelSelection}, and any external
 * {@code ShaderFunctionGraph} <b>resource dependencies</b> the graph references (keyed by their resource
 * path). Bundling the dependencies makes a shared work portable: a downloader's graph resolves its
 * shader-function subgraphs from the package itself. The server treats the whole tag as opaque.
 */
public final class WorkPackage {

    private final WorkMeta meta;
    /** Graph serialized via {@link RenderTypeGraphResource#serializeGraph} (node model + Settings). */
    private final CompoundTag graphTag;
    private final ModelSelection model;
    /** External Shader-Function dependencies: pathWithType -> serialized resource tag. */
    private final Map<String, CompoundTag> resources;

    public WorkPackage(WorkMeta meta, CompoundTag graphTag, ModelSelection model) {
        this(meta, graphTag, model, Map.of());
    }

    public WorkPackage(WorkMeta meta, CompoundTag graphTag, ModelSelection model, Map<String, CompoundTag> resources) {
        this.meta = meta;
        this.graphTag = graphTag;
        this.model = model;
        this.resources = resources;
    }

    /** Build a package by serializing a live graph + its model selection (no extra dependencies). */
    public static WorkPackage create(WorkMeta meta, RenderTypeGraph graph, ModelSelection model) {
        return new WorkPackage(meta, RenderTypeGraphResource.INSTANCE.serializeGraph(graph), model);
    }

    public WorkMeta meta() {
        return meta;
    }

    public ModelSelection model() {
        return model;
    }

    public Map<String, CompoundTag> resources() {
        return resources;
    }

    /** Same payload, new metadata — used for rename, which keeps the uid + dependencies. */
    public WorkPackage withMeta(WorkMeta newMeta) {
        return new WorkPackage(newMeta, graphTag, model, resources);
    }

    /**
     * Deserialize a fresh live graph from the stored payload, with a resolver that resolves external
     * Shader-Function references from the package's own bundled {@link #resources}. So a displayed /
     * downloaded work compiles without needing the dependencies present in the local folder.
     */
    public RenderTypeGraph loadGraph() {
        return RenderTypeGraphResource.INSTANCE.deserializeGraph(graphTag.copy(), bundledResolver());
    }

    private IGraphReferenceResolver bundledResolver() {
        return new IGraphReferenceResolver() {
            @Override
            public Graph resolve(IResourcePath path) {
                if (path == null) return null;
                CompoundTag tag = resources.get(path.getPathWithType());
                if (tag == null) return null;
                var graph = ShaderFunctionGraphResource.INSTANCE.createGraph();
                graph.graphModel.deserialize(TagValueInput.create(
                        ProblemReporter.Collector.DISCARDING, Platform.getFrozenRegistry(), tag));
                return graph;
            }
        };
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("meta", meta.toTag());
        CompoundTag payload = new CompoundTag();
        payload.put("graph", graphTag);
        payload.put("model", model.toTag());
        CompoundTag resTag = new CompoundTag();
        resources.forEach(resTag::put);
        payload.put("resources", resTag);
        tag.put("payload", payload);
        return tag;
    }

    public static WorkPackage fromTag(CompoundTag tag) {
        WorkMeta meta = WorkMeta.fromTag(tag.getCompoundOrEmpty("meta"));
        CompoundTag payload = tag.getCompoundOrEmpty("payload");
        CompoundTag graphTag = payload.getCompoundOrEmpty("graph");
        ModelSelection model = ModelSelection.fromTag(payload.getCompoundOrEmpty("model"));
        Map<String, CompoundTag> resources = new HashMap<>();
        CompoundTag resTag = payload.getCompoundOrEmpty("resources");
        for (String key : resTag.keySet()) {
            resources.put(key, resTag.getCompoundOrEmpty(key));
        }
        return new WorkPackage(meta, graphTag, model, resources);
    }
}
