package com.lowdragmc.kilagraphdemo.client.scoreboard;

import com.lowdragmc.kilagraphdemo.block.AbstractScoreboardBlockEntity.Row;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/** Immutable snapshot the {@link ScoreboardRenderer} draws: heading, ranked rows, and the billboard facing. */
public class ScoreboardRenderState extends BlockEntityRenderState {
    public BlockPos pos = BlockPos.ZERO;
    public Direction facing = Direction.NORTH;
    public String title = "";
    public List<Row> rows = List.of();
    public float time;
}
