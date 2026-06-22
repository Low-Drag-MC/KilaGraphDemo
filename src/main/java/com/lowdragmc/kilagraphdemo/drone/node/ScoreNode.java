package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.network.chat.Component;

/** Reports the run's banked score so far. */
@NodeAttribute(name = "drone.score", group = "drone", graphTypes = DroneGraph.class)
public class ScoreNode extends AnnotatedNode {

    @OutputPort
    public int score;

    @Override
    public void evaluate(EvalContext ctx) {
        DroneApi api = DroneApi.from(ctx.getExecutor());
        ctx.setOutput("score", api != null ? api.sim().getScore() : 0);
    }

    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        nodeModel.setTooltip(Component.translatable("drone.score.tooltip"));
    }
}
