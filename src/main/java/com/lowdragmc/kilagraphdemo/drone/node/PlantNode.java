package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Plant a pumpkin seed on the drone's current cell. Costs {@value #DURATION} ticks. */
@NodeAttribute(name = "drone.plant", group = "drone", graphTypes = DroneGraph.class)
public class PlantNode extends DroneActionNode {

    public static final int DURATION = 3;

    @OutputPort
    public boolean planted;

    @Override
    protected int perform(DroneApi api, ExecContext ctx) {
        ctx.setOutput("planted", api.plant());
        return DURATION;
    }

    @Override
    protected int durationTicks() {
        return DURATION;
    }
}
