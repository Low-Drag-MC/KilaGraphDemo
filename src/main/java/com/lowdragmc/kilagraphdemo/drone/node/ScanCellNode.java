package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.farm.Stage;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * Inspects a cell relative to the drone ({@code dx, dz} offset). Exposes the most useful predicates
 * for strategy: whether it's plantable (fertile and empty), ripe (harvestable), rotten, and the
 * merge size of any pumpkin there (1 for a base pumpkin, 2-4 for a merged block's core).
 */
@NodeAttribute(name = "drone.scan", group = "drone", graphTypes = DroneGraph.class)
public class ScanCellNode extends AnnotatedNode {

    @InputPort
    public int dx;
    @InputPort
    public int dz;
    @OutputPort
    public boolean plantable;
    @OutputPort
    public boolean ripe;
    @OutputPort
    public boolean rotten;
    @OutputPort
    public int mergeSize;

    @Override
    public void evaluate(EvalContext ctx) {
        DroneApi api = DroneApi.from(ctx.getExecutor());
        if (api == null) {
            ctx.setOutput("plantable", false);
            ctx.setOutput("ripe", false);
            ctx.setOutput("rotten", false);
            ctx.setOutput("mergeSize", 0);
            return;
        }
        FarmSimulation sim = api.sim();
        int cx = api.x() + ctx.getInput("dx", Integer.class, 0);
        int cz = api.z() + ctx.getInput("dz", Integer.class, 0);
        Stage stage = sim.getStage(cx, cz);
        ctx.setOutput("plantable", sim.isFertile(cx, cz) && stage == Stage.EMPTY);
        ctx.setOutput("ripe", stage == Stage.RIPE);
        ctx.setOutput("rotten", stage == Stage.ROTTEN);
        ctx.setOutput("mergeSize", sim.getMergeSize(cx, cz));
    }
}
