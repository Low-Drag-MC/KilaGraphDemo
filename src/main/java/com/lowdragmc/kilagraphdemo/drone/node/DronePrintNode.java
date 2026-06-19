package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Drone-specific print: appends {@code value} to the run's log (see {@link DroneApi#log}), which is
 * synced to whoever has the programming UI open and shown in its Log Panel. A free (zero-tick) exec node.
 * Replaces KilaGraph's generic {@code exec_print}, which this graph hides.
 */
@NodeAttribute(name = "drone.print", group = "drone", graphTypes = DroneGraph.class)
public class DronePrintNode extends AnnotatedNode {

    @ExecInputPort
    public ExecutionFlow trigger;
    @ExecOutputPort
    public ExecutionFlow next;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
    }

    @Override
    public void execute(ExecContext ctx) {
        DroneApi api = DroneApi.from(ctx.getExecutor());
        if (api != null) {
            Object v = ctx.getInput("value").orElse(null);
            api.log(String.valueOf(v));
        }
        ctx.flow("next");
    }
}
