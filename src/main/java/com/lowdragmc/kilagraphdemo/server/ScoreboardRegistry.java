package com.lowdragmc.kilagraphdemo.server;

import com.lowdragmc.kilagraphdemo.block.AbstractScoreboardBlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks the live in-world scoreboard boards ({@link AbstractScoreboardBlockEntity}) so a leaderboard change
 * can push a fresh snapshot to exactly those boards — event-driven, never polled. Boards add themselves on BE
 * load and drop out on unload; {@link #notify(Channel)} fans the change out to every board on that channel.
 *
 * <p>All callers run on the server thread (the drone scorer's main-thread write-back and the works packet
 * handlers), so there is no real contention; the sets are still {@link Collections#synchronizedSet synchronized}
 * and iteration snapshots a copy so a board unloading mid-refresh can't trip a {@link java.util.ConcurrentModificationException}.</p>
 */
public final class ScoreboardRegistry {

    /** Which dataset a board mirrors. */
    public enum Channel { DRONE, WORK }

    private static final Map<Channel, Set<AbstractScoreboardBlockEntity>> BOARDS = new EnumMap<>(Channel.class);

    static {
        for (Channel c : Channel.values()) {
            // Weak keys so a board that vanishes without setRemoved (e.g. a forced unload) doesn't leak.
            BOARDS.put(c, Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>())));
        }
    }

    private ScoreboardRegistry() {
    }

    public static void register(Channel channel, AbstractScoreboardBlockEntity be) {
        BOARDS.get(channel).add(be);
    }

    public static void unregister(Channel channel, AbstractScoreboardBlockEntity be) {
        BOARDS.get(channel).remove(be);
    }

    /** Refresh every loaded board on {@code channel} from its source of truth. */
    public static void notify(Channel channel) {
        Set<AbstractScoreboardBlockEntity> set = BOARDS.get(channel);
        List<AbstractScoreboardBlockEntity> snapshot;
        synchronized (set) {
            snapshot = new ArrayList<>(set);
        }
        for (AbstractScoreboardBlockEntity be : snapshot) {
            be.refreshFromSource();
        }
    }
}
