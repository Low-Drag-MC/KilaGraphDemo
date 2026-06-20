package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.ExecSession;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Drives a player's {@link DroneGraph} program against a {@link FarmSimulation} as a coroutine:
 * each game {@link #tick} advances the farm by one step (growth/rot/merge) and, when the drone is
 * free, steps the {@link ExecSession} until an <b>action</b> node runs — pure logic nodes execute
 * for free within the tick, while the action occupies the drone for its duration. A per-tick step
 * budget aborts runaway logic loops so a bad program can't hang the server.
 *
 * <p>Single-threaded and deterministic given a fixed seed: re-running the same program over the same
 * field yields the same score, which is what makes the leaderboard fair.</p>
 */
public class DroneRuntime {

    /** Max logic steps in a single tick before we declare the program stuck in a loop. */
    public static final int MAX_STEPS_PER_TICK = 10_000;

    private final FarmSimulation sim;
    private final DroneApi api;
    private final ExecSession session;
    @Nullable
    private final NodeModel entry;

    private int busyUntil;
    private boolean finished;
    private boolean halted;
    /** Number of action nodes executed so far; used to "step to the next exec/action". */
    private long actionCount;

    public DroneRuntime(DroneGraph graph, DroneApi api, long seed) {
        this.api = api;
        this.sim = api.sim();
        EvaluationEnvironment env = new EvaluationEnvironment(
                new VariableStore(Map.of(DroneApi.ENV_KEY, api)), OptionalLong.of(seed));
        GraphExecutor executor = new GraphExecutor(graph, env);
        this.session = new ExecSession(executor);
        this.entry = findEntry(graph);
        if (entry != null) {
            session.begin(entry);
        } else {
            finished = true; // no entry node -> nothing to run
        }
    }

    /** Advance the run by one game tick. {@code currentTick} is the run-relative tick counter. */
    public void tick(int currentTick) {
        api.setTick(currentTick);
        sim.tick();
        if (finished || halted) return;
        if (currentTick < busyUntil) return; // drone mid-action

        int steps = 0;
        while (true) {
            if (!session.step()) {
                finished = true;
                return;
            }
            int busy = api.consumePendingBusy();
            if (busy > 0) {
                busyUntil = currentTick + busy;
                actionCount++;
                return;
            }
            if (++steps >= MAX_STEPS_PER_TICK) {
                halted = true; // stuck in a logic loop with no action
                return;
            }
        }
    }

    /** How many action nodes have executed; increments each time the drone starts a new timed action. */
    public long getActionCount() {
        return actionCount;
    }

    public boolean isFinished() {
        return finished;
    }

    /** True if the program was aborted for looping without taking an action. */
    public boolean isHalted() {
        return halted;
    }

    public boolean isBusy(int currentTick) {
        return currentTick < busyUntil;
    }

    public int getScore() {
        return sim.getScore();
    }

    public DroneApi api() {
        return api;
    }

    /**
     * UID of the node currently occupying the drone (for runtime graph highlight), or {@code null}.
     * Uses {@link ExecSession#lastExecuted()} — the node that actually ran — rather than the next node,
     * so the highlight tracks the executing action even inside a {@code While}/{@code For} body.
     */
    @Nullable
    public UUID currentNodeUid() {
        NodeModel n = session.lastExecuted();
        return n != null ? n.getUid() : null;
    }

    @Nullable
    private static NodeModel findEntry(DroneGraph graph) {
        for (var node : graph.graphModel.getNodeModels()) {
            if (node instanceof ICustomNodeModel cnm && cnm.getNode() instanceof EntryNode
                    && node instanceof NodeModel nm) {
                return nm;
            }
        }
        return null;
    }
}
