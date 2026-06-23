package com.lowdragmc.kilagraphdemo.server;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-world store of drone-farming leaderboard entries: one {@link Entry} per player, holding their
 * last submitted graph and its officially-scored result. Submitting a solution (via the station's
 * redstone pulse, or auto-submit when the owner leaves) stores the program here immediately so it can
 * be {@link #getProgram preloaded} when the player next places a station; the async scorer fills in the
 * score afterwards via {@link #recordScore}.
 *
 * <p>Persistence mirrors {@link WorksSavedData}: the {@link SavedData} is just a per-world holder and the
 * actual data lives in our own {@code leaderboard.nbt} under the world's {@code data/} dir.</p>
 */
public class DroneLeaderboard extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SavedDataType<DroneLeaderboard> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "drone_leaderboard"),
            DroneLeaderboard::new,
            serverLevel -> MapCodec.unitCodec(() -> new DroneLeaderboard(serverLevel)),
            DataFixTypes.LEVEL);

    /** One player's standing: their last submitted program plus its latest official score. */
    public record Entry(String playerName, int score, long ticks, long timestamp, CompoundTag program) {
        CompoundTag toTag(UUID player) {
            CompoundTag tag = new CompoundTag();
            tag.putString("uuid", player.toString());
            tag.putString("name", playerName);
            tag.putInt("score", score);
            tag.putLong("ticks", ticks);
            tag.putLong("timestamp", timestamp);
            tag.put("program", program);
            return tag;
        }

        @Nullable
        static Map.Entry<UUID, Entry> fromTag(CompoundTag tag) {
            try {
                UUID uuid = UUID.fromString(tag.getStringOr("uuid", ""));
                Entry entry = new Entry(
                        tag.getStringOr("name", ""),
                        tag.getIntOr("score", 0),
                        tag.getLongOr("ticks", 0L),
                        tag.getLongOr("timestamp", 0L),
                        tag.getCompoundOrEmpty("program"));
                return Map.entry(uuid, entry);
            } catch (IllegalArgumentException e) {
                return null; // malformed uuid — skip
            }
        }
    }

    private final Path dir;
    private final Map<UUID, Entry> entries = new HashMap<>();

    private DroneLeaderboard(ServerLevel level) {
        this.dir = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve(Kilagraphdemo.MODID + "_leaderboard");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to create leaderboard dir {}", dir, e);
        }
        load();
    }

    public static DroneLeaderboard get(ServerLevel level) {
        // Stored on the overworld so all dimensions share one leaderboard.
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- queries -----------------------------------------------------------------------------

    /** The player's last submitted program (for preloading a freshly placed station), or null. */
    @Nullable
    public CompoundTag getProgram(UUID player) {
        Entry entry = entries.get(player);
        return entry == null ? null : entry.program();
    }

    @Nullable
    public Entry getEntry(UUID player) {
        return entries.get(player);
    }

    /** All entries, highest score first (for a future leaderboard UI). */
    public List<Entry> ranking() {
        List<Entry> out = new ArrayList<>(entries.values());
        out.sort(Comparator.comparingInt(Entry::score).reversed());
        return out;
    }

    // ---- mutations ---------------------------------------------------------------------------

    /**
     * Record a player's submitted program right away, keeping any prior score until the async scorer
     * overwrites it via {@link #recordScore}. Storing the program here is what lets it preload on the
     * player's next station placement.
     */
    public void submit(UUID player, String name, CompoundTag program) {
        Entry prev = entries.get(player);
        int score = prev == null ? 0 : prev.score();
        long ticks = prev == null ? 0L : prev.ticks();
        entries.put(player, new Entry(name, score, ticks, System.currentTimeMillis(), program.copy()));
        save();
        ScoreboardRegistry.notify(ScoreboardRegistry.Channel.DRONE);
    }

    /** Overwrite a player's score with the freshly computed result (latest-overwrites policy). */
    public void recordScore(UUID player, String name, int score, long ticks) {
        Entry prev = entries.get(player);
        CompoundTag program = prev == null ? new CompoundTag() : prev.program();
        entries.put(player, new Entry(name, score, ticks, System.currentTimeMillis(), program));
        save();
        ScoreboardRegistry.notify(ScoreboardRegistry.Channel.DRONE);
    }

    // ---- persistence (our own file) ----------------------------------------------------------

    private Path file() {
        return dir.resolve("leaderboard.nbt");
    }

    private void load() {
        Path file = file();
        if (!Files.exists(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            for (Tag t : root.getListOrEmpty("entries")) {
                if (t instanceof CompoundTag ct) {
                    Map.Entry<UUID, Entry> parsed = Entry.fromTag(ct);
                    if (parsed != null) entries.put(parsed.getKey(), parsed.getValue());
                }
            }
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to load leaderboard", e);
        }
    }

    private void save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        entries.forEach((uuid, entry) -> list.add(entry.toTag(uuid)));
        root.put("entries", list);
        try {
            NbtIo.writeCompressed(root, file());
        } catch (IOException e) {
            LOGGER.error("[KilaGraphDemo] failed to save leaderboard", e);
        }
    }
}
