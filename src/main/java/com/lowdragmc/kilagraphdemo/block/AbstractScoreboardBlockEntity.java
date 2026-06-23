package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.server.ScoreboardRegistry;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Shared base for the two read-only leaderboard display blocks (drone scores, hologram likes). The whole board
 * — title plus the ranked rows — lives in one {@code @DescSynced} {@link CompoundTag} that the BER reads; the
 * server rebuilds it from the relevant data store only when that store changes (see {@link ScoreboardRegistry}),
 * so there is no per-tick work and no broadcast to boards that don't exist.
 *
 * <p>Sync is manual, mirroring {@link AbstractDroneBoardBlockEntity}: {@link #useAsyncThread()} is false and we
 * call {@code sync(false)} ourselves after each rebuild so only the changed board tag goes out.</p>
 */
public abstract class AbstractScoreboardBlockEntity extends BlockEntity implements ISyncPersistRPCBlockEntity {

    /** Maximum rows shown on a board (the rest of the ranking is dropped). */
    public static final int MAX_ROWS = 10;

    /** One leaderboard line: a primary {@code name}, optional {@code sub} (e.g. author), and an integer value. */
    public record Row(String name, String sub, int value) {
        public Row(String name, int value) {
            this(name, "", value);
        }
    }

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    /** Title + rows, pushed to chunk-tracking clients on change. Persisted so a reload shows it before the first refresh. */
    @Persisted
    @DescSynced
    private CompoundTag board = new CompoundTag();

    protected AbstractScoreboardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The registry channel this board belongs to (drives which data changes refresh it). */
    protected abstract ScoreboardRegistry.Channel channel();

    /** Heading drawn at the top of the board. */
    protected abstract String title();

    /** Pull the current ranking from this board's data store (server-side), highest first. */
    protected abstract List<Row> computeRows(ServerLevel level);

    /** The board snapshot for the BER: {@code {title:String, rows:[{name,sub,value}]}}. */
    public CompoundTag getBoard() {
        return board;
    }

    /** Rebuild {@link #board} from the data store and push it to tracking clients. Server-side only. */
    public void refreshFromSource() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        List<Row> rows = computeRows(serverLevel);
        CompoundTag tag = new CompoundTag();
        tag.putString("title", title());
        ListTag list = new ListTag();
        int n = Math.min(rows.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            Row row = rows.get(i);
            CompoundTag rt = new CompoundTag();
            rt.putString("name", row.name() == null ? "" : row.name());
            rt.putString("sub", row.sub() == null ? "" : row.sub());
            rt.putInt("value", row.value());
            list.add(rt);
        }
        tag.put("rows", list);
        this.board = tag;
        setChanged();
        sync(false);
    }

    // ---- lifecycle: register/unregister with the change registry --------------------------------

    /**
     * Called when the BE is added to the world — both on placement and on chunk load ({@code LevelChunk}
     * invokes it right after {@code setLevel}, so {@link #level} is set). We register the board and pull the
     * current standings so a freshly loaded board is populated immediately.
     */
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level instanceof ServerLevel) {
            ScoreboardRegistry.register(channel(), this);
            refreshFromSource();
        }
    }

    @Override
    public void setRemoved() {
        ScoreboardRegistry.unregister(channel(), this);
        super.setRemoved();
    }

    // ---- managed sync config -------------------------------------------------------------------

    /** Manual sync mode: we call {@code sync(false)} ourselves after rebuilding the board. */
    @Override
    public boolean useAsyncThread() {
        return false;
    }
}
