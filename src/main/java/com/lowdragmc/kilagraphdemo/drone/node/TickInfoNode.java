package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.network.chat.Component;

/** Reports the current run tick (runs have no fixed length, so there is no "remaining" any more). */
@NodeAttribute(name = "drone.tick_info", group = "drone", graphTypes = DroneGraph.class)
public class TickInfoNode extends AnnotatedNode {

    @OutputPort
    public int tick;

    @Override
    public void evaluate(EvalContext ctx) {
        DroneApi api = DroneApi.from(ctx.getExecutor());
        ctx.setOutput("tick", api != null ? api.tick() : 0);
    }

    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        nodeModel.setTooltip(Component.translatable("drone.tick_info.tooltip"));
    }
}
