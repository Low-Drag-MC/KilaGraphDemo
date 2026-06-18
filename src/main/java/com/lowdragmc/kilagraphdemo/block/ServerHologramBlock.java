package com.lowdragmc.kilagraphdemo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A server-driven variant of {@link HologramBlock}. It mounts and projects identically (inheriting FACING,
 * the per-face shapes and placement), but the work it displays is <b>persisted and synced by the server</b>
 * (per block) rather than chosen client-side. Right-clicking opens a server-only browser where ops/creative
 * players pick the displayed work; every client then lazily downloads that work only when it must render it.
 */
public class ServerHologramBlock extends HologramBlock {

    public ServerHologramBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServerHologramBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        // Open the client-only server browser. The client class is referenced only inside this guard, so a
        // dedicated server never classloads it.
        if (level.isClientSide()) {
            com.lowdragmc.kilagraphdemo.client.editor.HologramScreens.openServerBrowse(level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
