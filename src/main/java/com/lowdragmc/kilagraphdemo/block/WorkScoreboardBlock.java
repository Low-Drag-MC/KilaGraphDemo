package com.lowdragmc.kilagraphdemo.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The hologram-likes scoreboard block. */
public class WorkScoreboardBlock extends AbstractScoreboardBlock {

    public static final MapCodec<WorkScoreboardBlock> CODEC = simpleCodec(WorkScoreboardBlock::new);

    public WorkScoreboardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorkScoreboardBlockEntity(pos, state);
    }
}
