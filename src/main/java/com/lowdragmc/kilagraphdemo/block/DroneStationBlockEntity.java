package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.DroneRuntime;
import com.lowdragmc.kilagraphdemo.drone.FieldDetector;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.farm.FarmConfig;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Block entity for the {@link DroneStationBlock}. Server-authoritative host of a drone-farming run,
 * using LDLib2's managed sync ({@link ISyncPersistRPCBlockEntity}) in <b>manual</b> mode.
 *
 * <p>Three data tiers:</p>
 * <ul>
 *   <li><b>{@code @Persisted}</b> (disk): {@link #owner}, {@link #program}.</li>
 *   <li><b>{@code @DescSynced}</b> (render group, pushed to chunk-tracking clients only on change via
 *       {@code sync(false)}): {@link #cells}, drone cell and field geometry — everything a nearby
 *       player needs to render the board, whether or not they have the UI open.</li>
 *   <li><b>server-only transient</b>: the {@link DroneRuntime}, run lifecycle, score, tick — streamed
 *       only to players who have the menu UI open, via the menu's {@code UISyncManager} (see the
 *       block's {@code createUI}). Not broadcast to everyone.</li>
 * </ul>
 *
 * <p>Async auto-sync is disabled ({@link #useAsyncThread()} returns false); we call {@code sync(false)}
 * explicitly at state changes and each run tick, so only changed fields go out and only when they change.</p>
 */
public class DroneStationBlockEntity extends BlockEntity implements ISyncPersistRPCBlockEntity {

    /** A run lasts this many ticks before it is scored and finishes (~3 minutes). */
    public static final int TOTAL_TICKS = 3600;
    /** Fixed RNG seed: every run faces identical conditions, keeping the leaderboard fair. */
    public static final long RUN_SEED = 0L;
    /** Auto-abort a paused run (or an offline owner's run) after this many ticks. */
    public static final int MAX_PAUSE_TICKS = 20 * 60 * 5; // 5 minutes

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    // --- persisted (disk) ----------------------------------------------------------------------
    /**
     * Owning player's UUID as a string; empty until placed by a player. Also {@code @DescSynced} so the
     * client knows who owns the station — the menu UI gates the owner-only Upload/Run controls on it.
     */
    @Persisted
    @DescSynced
    private String owner = "";
    /** The last uploaded program graph (KGGraphModel NBT). */
    @Persisted
    private CompoundTag program = new CompoundTag();

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

    // --- server-only transient (streamed only to menu-open players via UISyncManager) -----------
    @Nullable
    private DroneRuntime runtime;
    private RunState runState = RunState.IDLE;
    private int runTick;
    private int score;
    private int pauseTicks;
    /** Newline-joined run log mirrored from the runtime each tick; streamed to menu-open players. */
    private String logText = "";

    public DroneStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DRONE_STATION_BE.get(), pos, state);
    }

    // ---- ownership / program -----------------------------------------------------------------

    public boolean hasOwner() {
        return !owner.isEmpty();
    }

    @Nullable
    public UUID getOwner() {
        return owner.isEmpty() ? null : UUID.fromString(owner);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner == null ? "" : owner.toString();
        setChanged();
        sync(false); // push the new owner to tracking clients so the menu can show owner-only controls
    }

    public boolean isOwner(UUID player) {
        return player != null && player.toString().equals(owner);
    }

    public CompoundTag getProgram() {
        return program;
    }

    public void setProgram(CompoundTag program) {
        this.program = program == null ? new CompoundTag() : program;
        setChanged();
    }

    // ---- live state accessors (read by the menu UISyncManager for open players) ----------------

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

    // ---- run control (server-side; callers must already have checked ownership) ----------------

    /**
     * Begin a run of {@code programTag} over the field beneath this station. Returns whether it started
     * (it won't if there is no fertile field below). Stores the program, resets the field, builds a
     * fresh deterministic runtime and starts running.
     */
    public boolean startRun(CompoundTag programTag) {
        return startRun(programTag, false);
    }

    /**
     * Begin a run; {@code startPaused} starts it in {@link RunState#PAUSED} (used by single-step from idle).
     */
    public boolean startRun(CompoundTag programTag, boolean startPaused) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        FieldDetector.Field field = FieldDetector.detect(serverLevel, getBlockPos());
        if (field == null) return false;

        setProgram(programTag);
        FarmSimulation sim = FarmSimulation.of(field.width(), field.height(), field.fertile(), FarmConfig.DEFAULT);
        int startX = clamp(getBlockPos().getX() - field.originX(), field.width());
        int startZ = clamp(getBlockPos().getZ() - field.originZ(), field.height());
        DroneApi api = new DroneApi(sim, startX, startZ, TOTAL_TICKS);
        DroneGraph graph = DroneGraphCodec.fromTag(programTag, serverLevel.registryAccess());

        this.runtime = new DroneRuntime(graph, api, RUN_SEED);
        this.runState = startPaused ? RunState.PAUSED : RunState.RUNNING;
        this.runTick = 0;
        this.score = 0;
        this.droneX = startX;
        this.droneZ = startZ;
        this.fieldWidth = field.width();
        this.fieldHeight = field.height();
        this.fieldOffX = field.originX() - getBlockPos().getX();
        this.fieldOffZ = field.originZ() - getBlockPos().getZ();
        this.pauseTicks = 0;
        this.logText = ""; // fresh run starts with an empty log
        captureCells(sim);
        sync(false);
        return true;
    }

    public void pauseRun() {
        if (runState == RunState.RUNNING) {
            runState = RunState.PAUSED;
            pauseTicks = 0;
        }
    }

    public void resumeRun() {
        if (runState == RunState.PAUSED) {
            runState = RunState.RUNNING;
            pauseTicks = 0;
        }
    }

    /**
     * Single-step: advance the run to the <em>next executed action</em> (not just one game tick). From
     * idle/finished this first uploads + starts the program paused; while running it pauses first. The
     * program graph is needed only for the start-from-idle case.
     */
    public void stepRun(CompoundTag programTag) {
        if (runState == RunState.IDLE || runState == RunState.FINISHED) {
            if (!startRun(programTag, true)) return;
        } else if (runState == RunState.RUNNING) {
            runState = RunState.PAUSED;
            pauseTicks = 0;
        }
        if (runState == RunState.PAUSED) {
            doStep();
        }
    }

    /** Run game ticks until the runtime starts its next action (or the run ends). Bounded by a guard. */
    private void doStep() {
        DroneRuntime rt = this.runtime;
        if (rt == null) return;
        long target = rt.getActionCount() + 1;
        int guard = 0;
        while ((runState == RunState.RUNNING || runState == RunState.PAUSED)
                && runtime != null && runtime.getActionCount() < target && guard++ < 100_000) {
            tickRun();
        }
    }

    /** Abort the run and reset to idle (the field clears; the next run rebuilds it). */
    public void stopRun() {
        runtime = null;
        runState = RunState.IDLE;
        pauseTicks = 0;
        clearField();
        sync(false);
    }

    // ---- server tick -------------------------------------------------------------------------

    /**
     * Server tick: applies run <em>policy</em> (abort when the owner goes offline or a pause drags on;
     * honor a single-step request) and delegates the run <em>mechanics</em> to {@link #tickRun()}.
     */
    public void serverTick() {
        if (!(level instanceof ServerLevel)) return;
        if (runState == RunState.RUNNING) {
            if (ownerOffline()) {
                stopRun();
                return;
            }
            tickRun();
        } else if (runState == RunState.PAUSED) {
            if (ownerOffline() || ++pauseTicks > MAX_PAUSE_TICKS) {
                stopRun();
            }
        }
    }

    /**
     * Advance the run by one tick of pure mechanics: drive the runtime, mirror the render group, and
     * finish when the program ends/halts or the tick budget is spent. Pushes only changed @DescSynced
     * fields to tracking clients ({@code sync(false)} is a no-op when nothing changed). Returns the
     * resulting {@link RunState}. Drives the run even with no online player (used by GameTests).
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

    private void finishRun() {
        runState = RunState.FINISHED;
        runtime = null;
        // M8: submit `score` for `owner` to the leaderboard here.
        // Keep the board as-is so the result stays visible after the program ends (a short program would
        // otherwise wipe a freshly-planted pumpkin the instant it runs out of nodes). The next run rebuilds
        // a fresh field; an explicit Stop is what clears the board back to idle.
        sync(false);
    }

    private boolean ownerOffline() {
        UUID o = getOwner();
        if (o == null) return true;
        return level == null || level.getServer() == null
                || level.getServer().getPlayerList().getPlayer(o) == null;
    }

    /** Snapshot the farm grid into {@link #cells}: {@code stage | (mergeSize << 8)} per cell. */
    private void captureCells(FarmSimulation sim) {
        int w = sim.getWidth();
        int h = sim.getHeight();
        int[] arr = new int[w * h];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                arr[z * w + x] = sim.getStage(x, z).ordinal() | (sim.getMergeSize(x, z) << 8);
            }
        }
        cells = arr;
    }

    private void clearField() {
        fieldWidth = 0;
        fieldHeight = 0;
        cells = new int[0];
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    // ---- managed sync config -----------------------------------------------------------------

    /** Manual sync mode: we call {@code sync(false)} ourselves rather than the async auto-sync thread. */
    @Override
    public boolean useAsyncThread() {
        return false;
    }
}
