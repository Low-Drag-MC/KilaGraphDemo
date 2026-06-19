package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The runtime surface a player's graph drives: the drone's logical position on the field, the farm
 * simulation it acts on, the current run tick, and a "busy" request so action nodes can cost game
 * ticks. One instance per run, injected into the graph's {@link com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment}
 * under {@link #ENV_KEY} and read back by nodes via {@code ctx.getExecutor().getEnvironment().variables().get(ENV_KEY)}.
 *
 * <p>Drone movement is grid-stepped within the field bounding box (it flies, so it may pass over
 * non-fertile gaps). All state is plain integers, keeping the run deterministic.</p>
 */
public class DroneApi {

    /** Variable-store key under which this api is injected into the evaluation environment. */
    public static final String ENV_KEY = "drone_api";

    /** Cap on retained log lines, so a chatty program can't grow the buffer without bound. */
    private static final int MAX_LOG_LINES = 200;

    private final FarmSimulation sim;
    private final int totalTicks;
    private int x;
    private int z;
    private int tick;
    /** Ticks the most recently executed action wants to occupy; consumed by the runtime. */
    private int pendingBusy;
    /** Lines emitted by print nodes this run; surfaced to the UI Log Panel. */
    private final java.util.List<String> logs = new java.util.ArrayList<>();

    public DroneApi(FarmSimulation sim, int startX, int startZ, int totalTicks) {
        this.sim = sim;
        this.x = startX;
        this.z = startZ;
        this.totalTicks = totalTicks;
    }

    /** Fetch the api injected into an executor's evaluation environment, or {@code null} if absent. */
    @Nullable
    public static DroneApi from(GraphExecutor executor) {
        Object o = executor.getEnvironment().variables().get(ENV_KEY);
        return o instanceof DroneApi a ? a : null;
    }

    public FarmSimulation sim() {
        return sim;
    }

    /** Append a line to this run's log (oldest lines drop past {@link #MAX_LOG_LINES}). */
    public void log(String line) {
        logs.add(line);
        while (logs.size() > MAX_LOG_LINES) {
            logs.remove(0);
        }
    }

    /** This run's log lines, oldest first. */
    public java.util.List<String> logs() {
        return logs;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public int tick() {
        return tick;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public int ticksRemaining() {
        return Math.max(0, totalTicks - tick);
    }

    public void setTick(int tick) {
        this.tick = tick;
    }

    /** Request that the just-executed action occupy {@code ticks} game ticks. */
    public void busy(int ticks) {
        this.pendingBusy = Math.max(0, ticks);
    }

    /** Read and clear the pending busy request. Returns 0 when the last step was not a timed action. */
    public int consumePendingBusy() {
        int b = pendingBusy;
        pendingBusy = 0;
        return b;
    }

    // ---- actions (operate at the drone's current cell) ----------------------------------------

    /** Step one cell in the given direction; fails (drone stays) if it would leave the field box. */
    public boolean move(Direction dir) {
        int nx = x + dir.getStepX();
        int nz = z + dir.getStepZ();
        if (nx < 0 || nx >= sim.getWidth() || nz < 0 || nz >= sim.getHeight()) return false;
        x = nx;
        z = nz;
        return true;
    }

    public boolean plant() {
        return sim.plant(x, z);
    }

    public int harvest() {
        return sim.harvest(x, z);
    }

    public boolean clear() {
        return sim.clear(x, z);
    }
}
