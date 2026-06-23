package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.server.DroneLeaderboard;
import com.lowdragmc.kilagraphdemo.server.ScoreboardRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Scoreboard showing the drone-farming leaderboard ({@link DroneLeaderboard}), highest score first. Refreshed
 * whenever a solution is submitted or (re)scored.
 */
public class DroneScoreboardBlockEntity extends AbstractScoreboardBlockEntity {

    public DroneScoreboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DRONE_SCOREBOARD_BE.get(), pos, state);
    }

    @Override
    protected ScoreboardRegistry.Channel channel() {
        return ScoreboardRegistry.Channel.DRONE;
    }

    @Override
    protected String title() {
        return "Drone Ranking";
    }

    @Override
    protected List<Row> computeRows(ServerLevel level) {
        List<Row> rows = new ArrayList<>();
        for (DroneLeaderboard.Entry entry : DroneLeaderboard.get(level).ranking()) {
            rows.add(new Row(entry.playerName(), entry.score()));
        }
        return rows;
    }
}
