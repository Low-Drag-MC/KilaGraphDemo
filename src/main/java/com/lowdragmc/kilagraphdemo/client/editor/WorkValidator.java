package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraphdemo.graph.WorkPackage;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pre-upload gate for a {@link WorkPackage}: a shared work must be self-contained and renderable for
 * everyone who downloads it. Two checks, run against the package's <b>bundled</b> dependencies (a
 * downloader's exact view — see {@link WorkPackage#loadGraph()} / {@link WorkPackage#resources()}):
 * <ol>
 *   <li><b>References resolve.</b> Every external {@code ShaderFunctionGraph} reference in the graph
 *       (recursively, since functions nest) must be present in the bundled resources — otherwise a
 *       function was deleted/never bundled and the downloaded work would render broken.</li>
 *   <li><b>Compiles.</b> The graph must compile without throwing.</li>
 * </ol>
 * Local saves are intentionally <i>not</i> gated (work-in-progress is allowed); only upload is.
 */
public final class WorkValidator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private WorkValidator() {
    }

    /** @param missingRefs path strings of external shader-function references absent from the bundled
     *                     resources (empty when none); @param compileFailed true if compilation threw. */
    public record Result(List<String> missingRefs, boolean compileFailed) {
        public boolean ok() {
            return missingRefs.isEmpty() && !compileFailed;
        }
    }

    public static Result validate(WorkPackage pkg) {
        Set<String> missing = new LinkedHashSet<>();
        collectMissing(pkg.loadGraph(), pkg.resources(), missing, new HashSet<>());

        boolean compileFailed = false;
        try {
            new ShaderGraphCompiler(pkg.loadGraph()).compile();
        } catch (RuntimeException e) {
            LOGGER.warn("[KilaGraphDemo] work {} failed to compile during upload validation",
                    pkg.meta().uid(), e);
            compileFailed = true;
        }
        return new Result(List.copyOf(missing), compileFailed);
    }

    /** Walk every external {@link SubgraphNodeModel} reference; record paths absent from {@code resources},
     *  recursing into bundled functions for their own (transitive) references. */
    private static void collectMissing(Graph graph, Map<String, CompoundTag> resources,
                                       Set<String> missing, Set<String> visited) {
        // Walk node *models*, not graph.getNodes(): SubgraphNodeModel is excluded from the INode view
        // (it is neither an INode nor an ICustomNodeModel), so getNodes() never surfaces it.
        for (AbstractNodeModel node : graph.graphModel.getNodeModels()) {
            if (!(node instanceof SubgraphNodeModel sub) || sub.getKind() != SubgraphNodeModel.Kind.EXTERNAL) {
                continue;
            }
            IResourcePath path = sub.getExternalPath();
            if (path == null) continue;
            String key = path.getPathWithType();
            CompoundTag tag = resources.get(key);
            if (tag == null) {
                missing.add(key);
                continue;
            }
            if (!visited.add(key)) continue;
            ShaderFunctionGraph nested = deserialize(tag);
            if (nested != null) collectMissing(nested, resources, missing, visited);
        }
    }

    private static ShaderFunctionGraph deserialize(CompoundTag tag) {
        ShaderFunctionGraph graph = ShaderFunctionGraphResource.INSTANCE.createGraph();
        graph.graphModel.deserialize(TagValueInput.create(
                ProblemReporter.Collector.DISCARDING, Platform.getFrozenRegistry(), tag));
        return graph;
    }
}
