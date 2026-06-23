package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.drone.DroneScoring;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.kilagraphdemo.server.DroneLeaderboard;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Block entity for the {@link DroneStationBlock}: the owner-editable host of a drone-farming run. Extends
 * {@link AbstractDroneBoardBlockEntity} for the shared field simulation, runtime stepping and render-group
 * sync, and adds the station-only concerns: ownership, a persisted program, run controls (pause/resume/step/
 * stop), and the submission policy (a redstone pulse or the owner leaving submits the solution and frees the
 * field for the next player).
 *
 * <p>Live run state (run state, tick, score, current node, program, log) is streamed only to players who
 * have the menu UI open, via the menu's {@code UISyncManager} (see {@link DroneStationBlock#createUI}).</p>
 */
public class DroneStationBlockEntity extends AbstractDroneBoardBlockEntity {
    /** Auto-abort a paused run (or an offline owner's run) after this many ticks. */
    public static final int MAX_PAUSE_TICKS = 20 * 60 * 10; // 5 minutes
    /** Owner this many blocks (or more) from the station auto-submits + frees the field for the next player. */
    public static final double SUBMIT_DISTANCE = 30.0;

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

    // --- server-only transient -----------------------------------------------------------------
    private int pauseTicks;
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

    /** The station exposes its persisted (uploaded) program — shown in the menu even when no run is active. */
    @Override
    public CompoundTag getProgram() {
        return program;
    }

    public void setProgram(CompoundTag program) {
        this.program = program == null ? new CompoundTag() : program;
        setChanged();
    }

    /** Running a graph also persists it as the station's program (so it survives reload / preloads later). */
    @Override
    protected void onRunStarted(CompoundTag program) {
        setProgram(program);
    }

    // ---- run control (server-side; callers must already have checked ownership) ----------------

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
        if (runtime == null) return;
        long target = runtime.getActionCount() + 1;
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
            // The scorer deserializes a fresh graph per seed off-thread (registryAccess is an immutable snapshot).
            DroneScoring.submitAsync(sl.getServer(), o, name, prog, sl.registryAccess());
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
}
