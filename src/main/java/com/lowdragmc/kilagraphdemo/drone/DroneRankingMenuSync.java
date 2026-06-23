package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraphdemo.block.DroneRankingBlockEntity;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Per-open-player sync for the drone ranking block's spectator menu UI. Like {@link DroneMenuSync} but for
 * the read-only leaderboard viewer: it streams just what the spectator needs — the run state and executing
 * node (to highlight the live run), the displayed program graph, and the selected entry's author / official
 * score / rank — to a player who actually has the menu open, over the menu's
 * {@link com.lowdragmc.lowdraglib2.gui.sync.UISyncManager}.
 *
 * <p>All values are server&rarr;client only ({@code *S2C}). The same set must be registered in the
 * <em>same order</em> on both {@code createUI} branches — the sync manager keys values by registration index.</p>
 */
public final class DroneRankingMenuSync {

    private DroneRankingMenuSync() {
    }

    /** The bindings registered for one open menu, exposed so the client UI can read the synced values. */
    public record Bindings(SimpleBinding<Integer> runState,
                           SimpleBinding<String> currentNode,
                           SimpleBinding<Tag> program,
                           SimpleBinding<String> authorName,
                           SimpleBinding<Integer> score,
                           SimpleBinding<Integer> rank) {
    }

    /**
     * Build and register the spectator sync values on {@code ui}'s sync manager.
     *
     * @param ui       the menu UI whose {@code syncManager} receives the values.
     * @param isRemote {@code true} on the client (receive only), {@code false} on the server (provide values).
     *                 Must reflect the menu's logical side ({@code level().isClientSide()}).
     * @param be       the ranking block entity; the server suppliers read it (may be {@code null} defensively).
     */
    public static Bindings register(ModularUI ui, boolean isRemote, @Nullable DroneRankingBlockEntity be) {
        SimpleBinding<Integer> runState = DataBindingBuilder
                .intValS2C(() -> be == null ? 0 : be.getRunState().ordinal())
                .name("drone_rank_run_state").initialValue(0).build(isRemote);
        SimpleBinding<String> currentNode = DataBindingBuilder
                .stringS2C(() -> be == null ? "" : be.currentNodeUid())
                .name("drone_rank_current_node").initialValue("").build(isRemote);
        SimpleBinding<Tag> program = DataBindingBuilder
                .tagS2C(() -> be == null ? new CompoundTag() : be.getProgram())
                .name("drone_rank_program").initialValue(new CompoundTag()).build(isRemote);
        SimpleBinding<String> authorName = DataBindingBuilder
                .stringS2C(() -> be == null ? "" : be.getAuthorName())
                .name("drone_rank_author").initialValue("").build(isRemote);
        SimpleBinding<Integer> score = DataBindingBuilder
                .intValS2C(() -> be == null ? 0 : be.getOfficialScore())
                .name("drone_rank_score").initialValue(0).build(isRemote);
        SimpleBinding<Integer> rank = DataBindingBuilder
                .intValS2C(() -> be == null ? 0 : be.getRank())
                .name("drone_rank_rank").initialValue(0).build(isRemote);

        // Fixed registration order — must match on both sides (the manager keys by index).
        ui.syncManager.registerSyncValue(runState.getSyncValue());
        ui.syncManager.registerSyncValue(currentNode.getSyncValue());
        ui.syncManager.registerSyncValue(program.getSyncValue());
        ui.syncManager.registerSyncValue(authorName.getSyncValue());
        ui.syncManager.registerSyncValue(score.getSyncValue());
        ui.syncManager.registerSyncValue(rank.getSyncValue());

        return new Bindings(runState, currentNode, program, authorName, score, rank);
    }
}
