package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.drone.DroneApi;
import com.lowdragmc.kilagraphdemo.drone.DroneRuntime;
import com.lowdragmc.kilagraphdemo.drone.DroneField;
import com.lowdragmc.kilagraphdemo.drone.DroneScoring;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.farm.FarmConfig;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.kilagraphdemo.server.DroneLeaderboard;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
    /** Fixed RNG seed: every run faces identical conditions, keeping the leaderboard fair. */
    public static final long RUN_SEED = 0L;
    /** Auto-abort a paused run (or an offline owner's run) after this many ticks. */
    public static final int MAX_PAUSE_TICKS = 20 * 60 * 10; // 5 minutes
    /** Owner this many blocks (or more) from the station auto-submits + frees the field for the next player. */
    public static final double SUBMIT_DISTANCE = 30.0;

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
    /** Set once the solution has been submitted; the block is being removed, so don't submit twice. */
    private boolean submitted;
    /** Last redstone power state, for rising-edge ("button press") detection in {@link #onRedstone}. */
    private boolean lastPowered;

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
     * Begin a run of {@code programTag} on this station's fixed play field. Always succeeds (the field is a
     * fixed {@link DroneField} grid, not tied to any blocks below). Stores the program, resets the field,
     * builds a fresh deterministic runtime and starts running.
     */
    public boolean startRun(CompoundTag programTag) {
        return startRun(programTag, false);
    }

    /**
     * Begin a run; {@code startPaused} starts it in {@link RunState#PAUSED} (used by single-step from idle).
     * The scenario matches {@link DroneScoring} exactly so the preview is what gets scored: a fixed
     * {@link DroneField#WIDTH}x{@link DroneField#HEIGHT} field with the drone starting on the station,
     * <em>outside</em> the field at {@code (START_X, START_Z)}.
     */
    public boolean startRun(CompoundTag programTag, boolean startPaused) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        setProgram(programTag);
        FarmSimulation sim = FarmSimulation.allFertile(DroneField.WIDTH, DroneField.HEIGHT, FarmConfig.DEFAULT, RUN_SEED);
        DroneApi api = new DroneApi(sim, DroneField.START_X, DroneField.START_Z);
        DroneGraph graph = DroneGraphCodec.fromTag(programTag, serverLevel.registryAccess());

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
     * Server tick: first applies the <em>auto-submit</em> policy (the owner leaving — offline or
     * {@link #SUBMIT_DISTANCE} blocks away — submits their solution and frees the field for the next
     * player), then the run <em>policy</em> (abort an over-long pause) and delegates the run
     * <em>mechanics</em> to {@link #tickRun()}.
     */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (hasOwner()) {
            ServerPlayer p = serverLevel.getServer().getPlayerList().getPlayer(getOwner());
            boolean ownerGone = p == null || p.level() != level
                    || p.distanceToSqr(Vec3.atCenterOf(getBlockPos())) > SUBMIT_DISTANCE * SUBMIT_DISTANCE;
            if (ownerGone) {
                submitAndRemove(p == null ? "offline" : "far");
                return;
            }
        }
        if (runState == RunState.RUNNING) {
            tickRun();
        } else if (runState == RunState.PAUSED) {
            if (++pauseTicks > MAX_PAUSE_TICKS) {
                stopRun();
            }
        }
    }

    // ---- submission / leaderboard ------------------------------------------------------------

    /**
     * A redstone rising edge (e.g. a button press next to the station) submits the owner's solution.
     */
    public void onRedstone(boolean powered) {
        if (powered && !lastPowered) {
            submitAndRemove("redstone");
        }
        lastPowered = powered;
    }

    /**
     * Submit the owner's current program for official async scoring, store it for preload on their next
     * placement, then remove the block (no drop) to free the field. Idempotent — guarded by
     * {@link #submitted} since the redstone, far and offline triggers can overlap within a tick.
     */
    public void submitAndRemove(String reason) {
        if (submitted || !(level instanceof ServerLevel sl)) return;
        submitted = true;
        UUID o = getOwner();
        if (o != null) {
            CompoundTag prog = getProgram().copy();
            String name = ownerName(sl, o);
            DroneLeaderboard.get(sl).submit(o, name, prog);
            DroneGraph graph = DroneGraphCodec.fromTag(prog, sl.registryAccess());
            DroneScoring.submitAsync(sl.getServer(), o, name, graph);
        }
        runtime = null;
        sl.removeBlock(getBlockPos(), false); // silent: free the field for the next player, no item drop
    }

    /** Best-known display name for the owner: online name, else last-stored, else the raw UUID. */
    private String ownerName(ServerLevel sl, UUID o) {
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(o);
        if (p != null) return p.getName().getString();
        DroneLeaderboard.Entry entry = DroneLeaderboard.get(sl).getEntry(o);
        if (entry != null && !entry.playerName().isEmpty()) return entry.playerName();
        return o.toString();
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
        // This is only the in-world *preview* finishing — it does NOT submit to the leaderboard. The
        // official score comes from {@link DroneScoring#submitAsync}, run on a standardized field when the
        // player submits (redstone pulse / owner leaves). Keep the board as-is so the result stays visible
        // after the program ends; an explicit Stop is what clears it back to idle.
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
    private void captureCells(FarmSimulation sim) {
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

    private void clearField() {
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
