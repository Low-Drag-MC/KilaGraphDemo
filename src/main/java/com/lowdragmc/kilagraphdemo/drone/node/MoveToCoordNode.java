package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/**
 * Fly the drone straight to a target cell {@code (x, z)} on the field. The cost is distance-based — one
 * tick per cell of Manhattan distance — so this is no cheaper than chaining the same number of single
 * {@code Move}s, just more convenient. {@code moved} reports whether the target was inside the field
 * (a target outside the field leaves the drone where it is).
 */
@NodeAttribute(name = "drone.move_to", group = "drone", graphTypes = DroneGraph.class)
public class MoveToCoordNode extends DroneActionNode {

    @InputPort
    public int x;
    @InputPort
    public int z;
    @OutputPort
    public boolean moved;

    @Override
    protected int perform(DroneApi api, ExecContext ctx) {
        int tx = ctx.getInput("x", Integer.class, api.x());
        int tz = ctx.getInput("z", Integer.class, api.z());
        int distance = Math.abs(tx - api.x()) + Math.abs(tz - api.z());
        boolean ok = api.moveTo(tx, tz);
        ctx.setOutput("moved", ok);
        return ok ? Math.max(1, distance) : 1;
    }

    @Override
    protected int durationTicks() {
        return 1; // variable (distance-based); tooltip is a plain description.
    }

    @Override
    protected Component tooltipComponent() {
        return Component.translatable(tooltipKey());
    }
}
