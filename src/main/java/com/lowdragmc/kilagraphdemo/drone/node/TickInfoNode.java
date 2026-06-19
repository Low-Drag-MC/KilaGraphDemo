package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Reports the current run tick and how many ticks remain before the run ends. */
@NodeAttribute(name = "drone.tick_info", group = "drone", graphTypes = DroneGraph.class)
public class TickInfoNode extends AnnotatedNode {

    @OutputPort
    public int tick;
    @OutputPort
    public int remaining;

    @Override
    public void evaluate(EvalContext ctx) {
        DroneApi api = DroneApi.from(ctx.getExecutor());
        ctx.setOutput("tick", api != null ? api.tick() : 0);
        ctx.setOutput("remaining", api != null ? api.ticksRemaining() : 0);
    }
}
