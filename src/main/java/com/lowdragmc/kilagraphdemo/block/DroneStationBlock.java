package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.drone.DroneMenuSync;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * The drone programming station: a block that owns a single virtual drone (rendered by its block
 * entity) and the farm field of connected {@link FertileSoilBlock} beneath it. Right-click opens the
 * owner's programming UI as a vanilla menu ({@link BlockUIMenuType.BlockUI}), so run state streams only
 * to players who actually have it open (see {@link #createUI}). Breaking the block removes the drone and
 * any run.
 *
 * <p>Model/texture currently reuse a vanilla block as a placeholder.</p>
 */
public class DroneStationBlock extends Block implements EntityBlock, BlockUIMenuType.BlockUI {

    public DroneStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DroneStationBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof DroneStationBlockEntity be) {
            be.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        // Open server-side; the menu framework then opens the matching screen on the client.
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
     * branch builds the editor (and only there is the client-only UI class referenced, so a dedicated
     * server never classloads it); the server branch returns an empty shell whose {@code syncManager}
     * streams this player's run state via {@link DroneMenuSync}.
     */
    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        DroneStationBlockEntity be = holder.player.level().getBlockEntity(holder.pos) instanceof DroneStationBlockEntity b
                ? b : null;
        if (holder.player.level().isClientSide()) {
            return com.lowdragmc.kilagraphdemo.client.drone.DroneStationClientUI.build(holder, be);
        }
        ModularUI ui = new ModularUI(UI.of(new UIElement()), holder.player);
        DroneMenuSync.register(ui, false, be);
        return ui;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModRegistries.DRONE_STATION_BE.get()) return null;
        return (lvl, p, s, be) -> ((DroneStationBlockEntity) be).serverTick();
    }
}
