package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/** Idle the drone for a number of ticks (lets pumpkins grow). Occupies {@code ticks} game ticks. */
@NodeAttribute(name = "drone.wait", group = "drone", graphTypes = DroneGraph.class)
public class WaitNode extends DroneActionNode {

    @InputPort
    public int ticks = 20;

    @Override
    protected int perform(DroneApi api, ExecContext ctx) {
        return Math.max(1, ctx.getInput("ticks", Integer.class, 20));
    }

    @Override
    protected int durationTicks() {
        return ticks;
    }

    /** Variable cost (the {@code ticks} input), so the tooltip is a plain description. */
    @Override
    protected Component tooltipComponent() {
        return Component.translatable(tooltipKey());
    }
}
