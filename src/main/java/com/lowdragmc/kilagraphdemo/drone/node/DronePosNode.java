package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Reports the drone's current cell coordinates on the field. */
@NodeAttribute(name = "drone.pos", group = "drone", graphTypes = DroneGraph.class)
public class DronePosNode extends AnnotatedNode {

    @OutputPort
    public int x;
    @OutputPort
    public int z;

    @Override
    public void evaluate(EvalContext ctx) {
        DroneApi api = DroneApi.from(ctx.getExecutor());
        ctx.setOutput("x", api != null ? api.x() : 0);
        ctx.setOutput("z", api != null ? api.z() : 0);
    }
}
