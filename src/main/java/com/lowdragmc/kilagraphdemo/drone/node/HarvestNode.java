package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * Harvest a ripe pumpkin (or whole merged block) at the drone's current cell, banking its score.
 * Outputs the points gained (0 if nothing was harvestable). Costs {@value #DURATION} ticks.
 */
@NodeAttribute(name = "drone.harvest", group = "drone", graphTypes = DroneGraph.class)
public class HarvestNode extends DroneActionNode {

    public static final int DURATION = 3;

    @OutputPort
    public int gained;

    @Override
    protected int perform(DroneApi api, ExecContext ctx) {
        ctx.setOutput("gained", api.harvest());
        return DURATION;
    }

    @Override
    protected int durationTicks() {
        return DURATION;
    }
}
