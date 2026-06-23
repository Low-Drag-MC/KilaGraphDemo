package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.kilagraphdemo.server.DroneLeaderboard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Block entity for the {@link DroneRankingBlock}: a read-only leaderboard display. Each server tick it reads
 * the block's incoming redstone signal strength and uses it as a 0-based rank index into
 * {@link DroneLeaderboard#ranking()} (signal 0 = rank 1 = highest score), then continuously runs that
 * player's stored program on the standard field for an in-world preview — looping forever (see
 * {@link #finishRun()}). No ownership, no submission, no tick/score limit; everything is derived from the
 * leaderboard so nothing here is persisted.
 *
 * <p>Out-of-range handling (the spec's "越界问题要处理下，不崩溃就行"): a rank beyond the entry count, or an
 * empty / no-op program, simply idles with a blank board. A run loops only when the program actually
 * finishes/halts (see {@link #finishRun()}); a program that never finishes is left running indefinitely and
 * is only restarted as an overflow safety net at {@link #RUNTICK_OVERFLOW_GUARD}, far beyond any real run, so
 * {@code runTick} can never wrap — there is no fixed time limit and no mid-run reset.</p>
 */
public class DroneRankingBlockEntity extends AbstractDroneBoardBlockEntity {

    /**
     * Pure overflow safety net: only a program that never finishes/halts keeps incrementing {@code runTick}
     * forever, and at this threshold (~3.4 years of continuous running) we loop it so the int can't wrap. It
     * is never reached in normal viewing — the visible loop is "restart when the program finishes", not a
     * fixed time cap.
     */
    public static final int RUNTICK_OVERFLOW_GUARD = Integer.MAX_VALUE - 1000;

    // --- server-only transient (streamed to menu-open players via DroneRankingMenuSync) ---------
    /** The leaderboard entry currently on display; instance identity changes when its score updates. */
    @Nullable
    private DroneLeaderboard.Entry shown;
    private String authorName = "";
    private int officialScore;
    /** 1-based rank currently selected by the redstone signal (signal strength + 1). */
    private int rank;

    public DroneRankingBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DRONE_RANKING_BE.get(), pos, state);
    }

    // ---- accessors read by the menu sync suppliers --------------------------------------------

    public String getAuthorName() {
        return authorName;
    }

    public int getOfficialScore() {
        return officialScore;
    }

    public int getRank() {
        return rank;
    }

    // ---- server tick --------------------------------------------------------------------------

    /**
     * Pick the rank selected by the current redstone strength and keep its run going. Re-selects whenever the
     * signal points at a different entry (or that entry's score was updated), and loops the run for a
     * continuous display.
     */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        int index = serverLevel.getBestNeighborSignal(getBlockPos()); // 0..15
        List<DroneLeaderboard.Entry> ranking = DroneLeaderboard.get(serverLevel).ranking();
        rank = index + 1;

        if (index >= ranking.size()) {
            // No entry at this rank — blank board + empty author/score, no crash.
            shown = null;
            authorName = "";
            officialScore = 0;
            goIdle();
            return;
        }

        DroneLeaderboard.Entry entry = ranking.get(index);
        if (entry != shown) {
            // Selection changed (a different rank, or this player's score was just re-scored): (re)start.
            shown = entry;
            authorName = entry.playerName();
            officialScore = entry.score();
            if (!startDemo(entry.program())) {
                goIdle(); // empty / unrunnable program — don't tight-loop a no-op
            }
            return;
        }

        // Same selection: keep the run going. It loops on its own when the program finishes (finishRun); the
        // only check here is the far-off overflow guard for a program that never finishes — no time limit.
        if (runState == RunState.RUNNING) {
            tickRun();
            if (runTick > RUNTICK_OVERFLOW_GUARD) {
                restart();
            }
        }
    }

    /** Start a fresh display run; returns false for an empty program (caller then idles). */
    private boolean startDemo(CompoundTag program) {
        if (program == null || program.isEmpty()) return false;
        return startRun(program, false);
    }

    /** Restart the currently shown program from the top (fresh field, {@code runTick} reset to 0). */
    private void restart() {
        startRun(lastProgram, false);
    }

    /** Clear the board to an idle, blank state (keeps the author/score/rank labels as last set). */
    private void goIdle() {
        if (runState == RunState.IDLE && runtime == null && getFieldWidth() == 0) {
            return; // already idle — nothing to push
        }
        runtime = null;
        runState = RunState.IDLE;
        clearField();
        sync(false);
    }

    /**
     * Loop the display: when the program finishes or halts, restart it for a continuous demo — unless it was
     * a degenerate program that took no actions (so we don't restart a no-op every tick), in which case idle.
     */
    @Override
    protected void finishRun() {
        long actions = runtime == null ? 0 : runtime.getActionCount();
        if (actions <= 0) {
            goIdle();
            return;
        }
        restart();
    }
}
