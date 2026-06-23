package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.graph.ServerWorkEntry;
import com.lowdragmc.kilagraphdemo.server.ScoreboardRegistry;
import com.lowdragmc.kilagraphdemo.server.WorksSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Scoreboard showing the most-liked shared hologram works ({@link WorksSavedData}), most likes first. Refreshed
 * whenever a work is liked/unliked, uploaded or deleted.
 */
public class WorkScoreboardBlockEntity extends AbstractScoreboardBlockEntity {

    /** Public board: we don't care which player "liked" an entry, only the totals. */
    private static final UUID NO_REQUESTER = new UUID(0L, 0L);

    public WorkScoreboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.WORK_SCOREBOARD_BE.get(), pos, state);
    }

    @Override
    protected ScoreboardRegistry.Channel channel() {
        return ScoreboardRegistry.Channel.WORK;
    }

    @Override
    protected String title() {
        return "Top Holograms";
    }

    @Override
    protected List<Row> computeRows(ServerLevel level) {
        List<ServerWorkEntry> entries = WorksSavedData.get(level).listEntries(NO_REQUESTER);
        entries.sort(Comparator.comparingInt(ServerWorkEntry::likeCount).reversed());
        List<Row> rows = new ArrayList<>();
        for (ServerWorkEntry e : entries) {
            rows.add(new Row(e.meta().title(), e.meta().authorName(), e.likeCount()));
        }
        return rows;
    }
}
