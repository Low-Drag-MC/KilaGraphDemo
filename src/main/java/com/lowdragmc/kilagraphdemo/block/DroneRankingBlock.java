package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.drone.DroneRankingMenuSync;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The drone ranking display block: a public, read-only viewer of the {@code DroneLeaderboard}. Its incoming
 * redstone signal strength selects which rank to show (0 = the top score), and its block entity continuously
 * runs that solution for an in-world preview ({@code DroneStationRenderer}). Right-click opens a spectator
 * menu (the run graph in read-only mode plus the author / score / rank) — anyone may open it; there is no
 * owner, no editing and no submission.
 *
 * <p>Model/texture currently reuse the drone station's placeholder.</p>
 */
public class DroneRankingBlock extends Block implements EntityBlock, BlockUIMenuType.BlockUI {

    public DroneRankingBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DroneRankingBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        // Open server-side; the menu framework then opens the matching screen on the client. No owner gate —
        // the ranking board is a public display, so any player may open it.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockUIMenuType.openUI(serverPlayer, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * Build the menu UI. Called on both sides by {@code ModularUIContainerMenu}'s constructor: the client
     * branch builds the read-only spectator view (and only there is the client-only UI class referenced, so a
     * dedicated server never classloads it); the server branch returns an empty shell whose {@code syncManager}
     * streams the selected run's state via {@link DroneRankingMenuSync}.
     */
    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        DroneRankingBlockEntity be = holder.player.level().getBlockEntity(holder.pos) instanceof DroneRankingBlockEntity b
                ? b : null;
        if (holder.player.level().isClientSide()) {
            return com.lowdragmc.kilagraphdemo.client.drone.DroneRankingClientUI.build(holder, be);
        }
        ModularUI ui = new ModularUI(UI.of(new UIElement()), holder.player);
        ui.shouldCloseOnKeyInventory(false);
        DroneRankingMenuSync.register(ui, false, be);
        return ui;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModRegistries.DRONE_RANKING_BE.get()) return null;
        return (lvl, p, s, be) -> ((DroneRankingBlockEntity) be).serverTick();
    }
}
