package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Per-open-player run-state sync for the drone station menu UI. Unlike the {@code @DescSynced} render
 * group on the block entity (broadcast to everyone tracking the chunk, for the board renderer), these
 * values travel only to a player who actually has the station's menu open, over the menu's
 * {@link com.lowdragmc.lowdraglib2.gui.sync.UISyncManager}: the framework sends initial values when the
 * menu opens and only-changed deltas each tick, and cleans up when the menu closes.
 *
 * <p>All five values are server&rarr;client only ({@code *S2C} bindings). The same set must be registered
 * in the <em>same order</em> on both sides — the sync manager keys values by registration index — so both
 * {@code createUI} branches call {@link #register} with the identical sequence below. On the server the
 * suppliers read the live block entity; on the client they are unused (the client only receives), and the
 * synced value is read back via {@code binding.getSyncValue().getValue()}.</p>
 */
public final class DroneMenuSync {

    private DroneMenuSync() {
    }

    /** The bindings registered for one open menu, exposed so the client UI can read the synced values. */
    public record Bindings(SimpleBinding<Integer> runState,
                           SimpleBinding<Integer> runTick,
                           SimpleBinding<Integer> score,
                           SimpleBinding<String> currentNode,
                           SimpleBinding<Tag> program,
                           SimpleBinding<String> log) {
    }

    /**
     * Build and register the run-state sync values on {@code ui}'s sync manager.
     *
     * @param ui       the menu UI whose {@code syncManager} receives the values.
     * @param isRemote {@code true} on the client (receive only), {@code false} on the server (provide values).
     *                 Must reflect the menu's logical side ({@code level().isClientSide()}); do not use
     *                 {@code build()} (it keys off the physical dist and is wrong on the integrated server).
     * @param be       the station block entity; the server suppliers read it (may be {@code null} defensively).
     */
    public static Bindings register(ModularUI ui, boolean isRemote, @Nullable DroneStationBlockEntity be) {
        SimpleBinding<Integer> runState = DataBindingBuilder
                .intValS2C(() -> be == null ? 0 : be.getRunState().ordinal())
                .name("drone_run_state").initialValue(0).build(isRemote);
        SimpleBinding<Integer> runTick = DataBindingBuilder
                .intValS2C(() -> be == null ? 0 : be.getRunTick())
                .name("drone_run_tick").initialValue(0).build(isRemote);
        SimpleBinding<Integer> score = DataBindingBuilder
                .intValS2C(() -> be == null ? 0 : be.getScore())
                .name("drone_score").initialValue(0).build(isRemote);
        SimpleBinding<String> currentNode = DataBindingBuilder
                .stringS2C(() -> be == null ? "" : be.currentNodeUid())
                .name("drone_current_node").initialValue("").build(isRemote);
        SimpleBinding<Tag> program = DataBindingBuilder
                .tagS2C(() -> be == null ? new CompoundTag() : be.getProgram())
                .name("drone_program").initialValue(new CompoundTag()).build(isRemote);
        SimpleBinding<String> log = DataBindingBuilder
                .stringS2C(() -> be == null ? "" : be.getLogText())
                .name("drone_log").initialValue("").build(isRemote);

        // Fixed registration order — must match on both sides (the manager keys by index).
        ui.syncManager.registerSyncValue(runState.getSyncValue());
        ui.syncManager.registerSyncValue(runTick.getSyncValue());
        ui.syncManager.registerSyncValue(score.getSyncValue());
        ui.syncManager.registerSyncValue(currentNode.getSyncValue());
        ui.syncManager.registerSyncValue(program.getSyncValue());
        ui.syncManager.registerSyncValue(log.getSyncValue());

        return new Bindings(runState, runTick, score, currentNode, program, log);
    }
}
