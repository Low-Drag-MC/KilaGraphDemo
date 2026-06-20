package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Clear whatever occupies the drone's current cell (crop or rotten pumpkin). Costs {@value #DURATION} ticks. */
@NodeAttribute(name = "drone.clear", group = "drone", graphTypes = DroneGraph.class)
public class ClearNode extends DroneActionNode {

    public static final int DURATION = 2;

    @OutputPort
    public boolean cleared;

    @Override
    protected int perform(DroneApi api, ExecContext ctx) {
        ctx.setOutput("cleared", api.clear());
        return DURATION;
    }
}
