package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.DroneField;
import com.lowdragmc.kilagraphdemo.drone.DroneRuntime;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.farm.FarmConfig;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared base for block entities that host a drone-farming run on the fixed {@link DroneField} play field
 * and render it through {@code DroneStationRenderer}. Factored out of {@link DroneStationBlockEntity} so the
 * read-only {@link DroneRankingBlockEntity} (a leaderboard display) can reuse the exact same simulation,
 * runtime stepping and render-group sync without duplicating the tricky {@code @DescSynced} delta handling.
 *
 * <p>Two of the station's three data tiers live here:</p>
 * <ul>
 *   <li><b>{@code @DescSynced}</b> render group, pushed to chunk-tracking clients only on change via
 *       {@code sync(false)}: {@link #cells}, the drone cell and field geometry — everything the BER needs.</li>
 *   <li><b>server-only transient</b>: the {@link DroneRuntime}, run lifecycle, score, tick and log. Subclasses
 *       stream these to interested clients however they like (e.g. via a menu {@code UISyncManager}).</li>
 * </ul>
 *
 * <p>Async auto-sync is disabled ({@link #useAsyncThread()} returns false); we call {@code sync(false)}
 * explicitly at state changes and each run tick, so only changed fields go out and only when they change.</p>
 */
public abstract class AbstractDroneBoardBlockEntity extends BlockEntity implements ISyncPersistRPCBlockEntity {

    /** Fixed RNG seed: every run faces identical conditions, keeping the leaderboard fair. */
    public static final long RUN_SEED = 0L;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    // --- render group (@DescSynced to chunk-tracking clients, manual sync) ----------------------
    /** One int per cell: {@code stageOrdinal | (mergeSize << 8)}. Empty when no run is active. */
    @DescSynced
    private int[] cells = new int[0];
    @DescSynced
    private int droneX;
    @DescSynced
    private int droneZ;
    @DescSynced
    private int fieldWidth;
    @DescSynced
    private int fieldHeight;
    @DescSynced
    private int fieldOffX;
    @DescSynced
    private int fieldOffZ;

    // --- server-only transient -----------------------------------------------------------------
    @Nullable
    protected DroneRuntime runtime;
    protected RunState runState = RunState.IDLE;
    protected int runTick;
    protected int score;
    /** Newline-joined run log mirrored from the runtime each tick. */
    protected String logText = "";
    /** The program graph currently loaded into the run (the one (re)started); transient. */
    protected CompoundTag lastProgram = new CompoundTag();

    protected AbstractDroneBoardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- live state accessors -----------------------------------------------------------------

    public RunState getRunState() {
        return runState;
    }

    public int getRunTick() {
        return runTick;
    }

    public int getScore() {
        return score;
    }

    /** Newline-joined log lines emitted by print nodes this run (for the UI Log Panel). */
    public String getLogText() {
        return logText;
    }

    /** UID of the node about to execute (for runtime graph highlight), or empty string. */
    public String currentNodeUid() {
        UUID uid = runtime == null ? null : runtime.currentNodeUid();
        return uid == null ? "" : uid.toString();
    }

    /** The program graph currently driving the run; subclasses may override to expose a stored program. */
    public CompoundTag getProgram() {
        return lastProgram;
    }

    // ---- render-group accessors (for the BER) -------------------------------------------------

    public int[] getCells() {
        return cells;
    }

    public int getDroneX() {
        return droneX;
    }

    public int getDroneZ() {
        return droneZ;
    }

    public int getFieldWidth() {
        return fieldWidth;
    }

    public int getFieldHeight() {
        return fieldHeight;
    }

    public int getFieldOffX() {
        return fieldOffX;
    }

    public int getFieldOffZ() {
        return fieldOffZ;
    }

    // ---- run control (server-side) ------------------------------------------------------------

    public boolean startRun(CompoundTag programTag) {
        return startRun(programTag, false);
    }

    /**
     * Begin a run of {@code programTag} on the fixed {@link DroneField} grid. {@code startPaused} starts it
     * in {@link RunState#PAUSED}. The scenario matches {@code DroneScoring} exactly so the preview is what
     * gets scored: a fixed {@link DroneField#WIDTH}x{@link DroneField#HEIGHT} field with the drone starting
     * on the station, <em>outside</em> the field at {@code (START_X, START_Z)}.
     */
    public boolean startRun(CompoundTag programTag, boolean startPaused) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        this.lastProgram = programTag == null ? new CompoundTag() : programTag;
        onRunStarted(this.lastProgram);
        FarmSimulation sim = FarmSimulation.allFertile(DroneField.WIDTH, DroneField.HEIGHT, FarmConfig.DEFAULT, RUN_SEED);
        DroneApi api = new DroneApi(sim, DroneField.START_X, DroneField.START_Z);
        DroneGraph graph = DroneGraphCodec.fromTag(this.lastProgram, serverLevel.registryAccess());

        this.runtime = new DroneRuntime(graph, api, RUN_SEED);
        this.runState = startPaused ? RunState.PAUSED : RunState.RUNNING;
        this.runTick = 0;
        this.score = 0;
        this.droneX = DroneField.START_X;
        this.droneZ = DroneField.START_Z;
        this.fieldWidth = DroneField.WIDTH;
        this.fieldHeight = DroneField.HEIGHT;
        this.fieldOffX = DroneField.OFFSET_X;
        this.fieldOffZ = DroneField.OFFSET_Z;
        this.logText = ""; // fresh run starts with an empty log
        captureCells(sim);
        sync(false);
        return true;
    }

    /** Hook fired when a run (re)starts, before the runtime is built. Default no-op. */
    protected void onRunStarted(CompoundTag program) {
    }

    /**
     * Advance the run by one tick of pure mechanics: drive the runtime, mirror the render group, and finish
     * when the program ends/halts. Pushes only changed {@code @DescSynced} fields to tracking clients
     * ({@code sync(false)} is a no-op when nothing changed). Returns the resulting {@link RunState}.
     */
    public RunState tickRun() {
        DroneRuntime rt = this.runtime;
        if (rt == null || (runState != RunState.RUNNING && runState != RunState.PAUSED)) {
            return runState;
        }
        rt.tick(runTick);
        runTick++;
        DroneApi api = rt.api();
        droneX = api.x();
        droneZ = api.z();
        score = rt.getScore();
        logText = String.join("\n", api.logs());
        captureCells(api.sim());
        // No fixed time limit: a run ends only when the program completes or halts (or via stop/pause).
        if (rt.isFinished() || rt.isHalted()) {
            finishRun();
        } else {
            sync(false);
        }
        return runState;
    }

    /**
     * Called when the run completes or halts. Default: freeze the board on {@link RunState#FINISHED}.
     * Subclasses may override (e.g. to loop the run for a continuous display).
     */
    protected void finishRun() {
        runState = RunState.FINISHED;
        runtime = null;
        sync(false);
    }

    /**
     * Snapshot the farm grid into {@link #cells}: {@code stage | (mergeSize << 8)} per cell.
     *
     * <p><b>Reuses the existing array in place when the size matches</b>, rather than allocating a fresh
     * one each tick. This is required for the {@code @DescSynced} delta to fire: LDLib2's
     * {@code DirectArrayRef} only marks an array field dirty when its <em>elements</em> change on the
     * <em>same</em> reference; a brand-new array each tick takes its reassignment branch, which marks only
     * the child refs and never the field itself, so {@code sync(false)} would never transmit it and the
     * client board would stay empty.</p>
     */
    protected void captureCells(FarmSimulation sim) {
        int w = sim.getWidth();
        int h = sim.getHeight();
        int n = w * h;
        int[] arr = cells.length == n ? cells : new int[n];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                arr[z * w + x] = sim.getStage(x, z).ordinal() | (sim.getMergeSize(x, z) << 8);
            }
        }
        cells = arr;
    }

    protected void clearField() {
        fieldWidth = 0;
        fieldHeight = 0;
        cells = new int[0];
    }

    // ---- managed sync config -----------------------------------------------------------------

    /** Manual sync mode: we call {@code sync(false)} ourselves rather than the async auto-sync thread. */
    @Override
    public boolean useAsyncThread() {
        return false;
    }
}
