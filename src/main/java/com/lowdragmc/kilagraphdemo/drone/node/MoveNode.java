package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Direction;

/** Move the drone one cell in a horizontal direction. Costs {@value #DURATION} ticks. */
@NodeAttribute(name = "drone.move", group = "drone", graphTypes = DroneGraph.class)
public class MoveNode extends DroneActionNode {

    public static final int DURATION = 10;

    @InputPort
    public Direction direction = Direction.NORTH;
    @OutputPort
    public boolean moved;

    @Override
    protected int perform(DroneApi api, ExecContext ctx) {
        Direction dir = ctx.getInput("direction", Direction.class, Direction.NORTH);
        ctx.setOutput("moved", api.move(dir));
        return DURATION;
    }
}
