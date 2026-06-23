package com.lowdragmc.kilagraphdemo.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for the read-only scoreboard display blocks. The block itself is a small pedestal; its block
 * entity renders the leaderboard billboard above it ({@code ScoreboardRenderer}). A horizontal {@link #FACING}
 * (set to face the placer) tells the renderer which way the billboard should face. There is no menu and no
 * ticker — the board updates only when its data store changes.
 */
public abstract class AbstractScoreboardBlock extends HorizontalDirectionalBlock implements EntityBlock {

    protected AbstractScoreboardBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the placer, so the billboard reads to whoever just set it down (furnace-style).
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
