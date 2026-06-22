package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.core.Direction;

/**
 * Move the drone one cell in a horizontal direction. Costs {@value #DURATION} ticks.
 *
 * <p>The {@code direction} input is defined dynamically (rather than as an annotated {@code @InputPort})
 * so it can use a custom configurator that offers only the four horizontal directions — the field is flat,
 * so {@code UP}/{@code DOWN} are meaningless.</p>
 */
@NodeAttribute(name = "drone.move", group = "drone", graphTypes = DroneGraph.class)
public class MoveNode extends DroneActionNode {

    public static final int DURATION = 4;
    /** Fallback when the port has no value yet (evaluate()'s default). */
    public static final Direction DEFAULT = Direction.NORTH;

    @OutputPort
    public boolean moved;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("direction", KGTypeHandles.handleFor(Direction.class))
                .withDefaultValue(DEFAULT)
                // Client-only configurator (loaded lazily inside the lambda, never on the server).
                .withConfigurable((vc, th) ->
                        com.lowdragmc.kilagraphdemo.client.editor.DirectionConfigurator.horizontal(vc));
    }

    @Override
    protected int perform(DroneApi api, ExecContext ctx) {
        Direction dir = ctx.getInput("direction", Direction.class, DEFAULT);
        ctx.setOutput("moved", api.move(dir));
        return DURATION;
    }

    @Override
    protected int durationTicks() {
        return DURATION;
    }
}
