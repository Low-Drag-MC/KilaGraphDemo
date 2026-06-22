package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.network.chat.Component;

/**
 * Base class for the drone's <b>action</b> nodes: each has a single {@code trigger} exec input and a
 * {@code next} exec output, performs one world action, and occupies a number of game ticks. The
 * runtime steps the program until an action runs, then pauses that drone for the action's duration
 * (this is what makes movement/planting cost time while pure logic nodes run for free).
 */
public abstract class DroneActionNode extends AnnotatedNode {

    @ExecInputPort
    public ExecutionFlow trigger;
    @ExecOutputPort
    public ExecutionFlow next;

    @Override
    public final void execute(ExecContext ctx) {
        DroneApi api = DroneApi.from(ctx.getExecutor());
        if (api != null) {
            int duration = perform(api, ctx);
            api.busy(Math.max(1, duration));
        }
        ctx.flow("next");
    }

    /** Perform the action against {@code api}; return how many ticks it should occupy (min 1). */
    protected abstract int perform(DroneApi api, ExecContext ctx);

    /** Representative tick cost surfaced as the {@code %s} argument of the node's tooltip. */
    protected abstract int durationTicks();

    /**
     * Install a hover tooltip describing the action and its tick cost. Fixed-cost actions use a
     * {@code "<name>.tooltip"} key with the duration as its single {@code %s} argument; variable-cost
     * actions ({@code Wait}, {@code MoveToCoord}) override {@link #tooltipComponent()} for a no-arg key.
     */
    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        nodeModel.setTooltip(tooltipComponent());
    }

    protected Component tooltipComponent() {
        return Component.translatable(tooltipKey(), durationTicks());
    }

    /** Tooltip lang key: the node's {@link NodeAttribute#name()} suffixed with {@code .tooltip}. */
    protected final String tooltipKey() {
        NodeAttribute attr = getClass().getAnnotation(NodeAttribute.class);
        return (attr == null ? getClass().getSimpleName() : attr.name()) + ".tooltip";
    }
}
