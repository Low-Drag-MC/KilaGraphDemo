package com.lowdragmc.kilagraphdemo.drone.node;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.farm.Stage;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * Inspects a field cell, reporting the most useful predicates for strategy: whether it's plantable
 * (fertile and empty), ripe (harvestable), rotten, and the merge size of any pumpkin there (1 for a base
 * pumpkin, 2-4 for a merged block's core).
 *
 * <p>The {@link Mode} option chooses how the cell is addressed:</p>
 * <ul>
 *   <li>{@link Mode#RELATIVE} (default) — {@code dx, dz} offset from the drone's current cell (look-ahead).</li>
 *   <li>{@link Mode#ABSOLUTE} — {@code x, z} absolute field coordinates, the same space as {@code Move To}.</li>
 * </ul>
 */
@NodeAttribute(name = "drone.scan", group = "drone", graphTypes = DroneGraph.class)
public class ScanCellNode extends AnnotatedNode {

    /** How the scanned cell is addressed. */
    public enum Mode { RELATIVE, ABSOLUTE }

    @Option
    public Mode mode = Mode.RELATIVE;

    @OutputPort
    public boolean plantable;
    @OutputPort
    public boolean ripe;
    @OutputPort
    public boolean rotten;
    @OutputPort
    public int mergeSize;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        // Relative addressing exposes dx/dz (offset from the drone); absolute exposes x/z (field coords).
        if (optionValue("mode", Mode.class, mode) == Mode.ABSOLUTE) {
            ctx.addInputPort("x", KGTypeHandles.handleFor(Integer.class)).withDefaultValue(0);
            ctx.addInputPort("z", KGTypeHandles.handleFor(Integer.class)).withDefaultValue(0);
        } else {
            ctx.addInputPort("dx", KGTypeHandles.handleFor(Integer.class)).withDefaultValue(0);
            ctx.addInputPort("dz", KGTypeHandles.handleFor(Integer.class)).withDefaultValue(0);
        }
    }

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
        int cx, cz;
        if (ctx.getOption("mode", Mode.class, mode) == Mode.ABSOLUTE) {
            cx = ctx.getInput("x", Integer.class, 0);
            cz = ctx.getInput("z", Integer.class, 0);
        } else {
            cx = api.x() + ctx.getInput("dx", Integer.class, 0);
            cz = api.z() + ctx.getInput("dz", Integer.class, 0);
        }
        Stage stage = sim.getStage(cx, cz);
        ctx.setOutput("plantable", sim.isFertile(cx, cz) && stage == Stage.EMPTY);
        ctx.setOutput("ripe", stage == Stage.RIPE);
        ctx.setOutput("rotten", stage == Stage.ROTTEN);
        ctx.setOutput("mergeSize", sim.getMergeSize(cx, cz));
    }

    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        nodeModel.setTooltip(Component.translatable("drone.scan.tooltip"));
    }
}
