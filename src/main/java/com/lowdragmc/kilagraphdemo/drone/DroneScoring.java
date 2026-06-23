package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.farm.FarmConfig;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.server.DroneLeaderboard;
import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Official, fair scoring of a submitted drone program. Every submission is scored on the same
 * <b>fixed virtual field</b> ({@link DroneField}, the same one the in-world preview uses) for a fixed tick
 * budget, so the leaderboard compares pure programming skill.
 *
 * <p>To keep the score robust against the field's Gaussian growth/rot jitter (a single seed could happen to
 * favour or punish a program), each submission is run once per seed in {@link #SCORING_SEEDS} and the
 * <b>median</b> score is taken. The seeds are <em>fixed</em>, so scoring stays fully deterministic and
 * reproducible — the same program always yields the same official score.</p>
 *
 * <p>The simulation is pure Java and single-threaded per instance ({@link DroneRuntime}), so the runs are
 * dispatched to a background daemon thread; only the final {@link DroneLeaderboard#recordScore write-back}
 * is hopped back onto the main server thread via {@link MinecraftServer#executeIfPossible}.</p>
 */
public final class DroneScoring {

    /**
     * The fixed seeds a submission is scored on; the median of the per-seed scores is the official score.
     * Fixed (not random) so the leaderboard stays deterministic and reproducible. An odd count keeps the
     * median a single well-defined element.
     */
    public static final long[] SCORING_SEEDS = {2048L, 7L, 31337L, 999983L, 424242L};

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How long a submitted program is allowed to run when scored. */
    public static final int TICK_BUDGET = 10_000;

    /** Single background thread; daemon so it never blocks shutdown. Lazily created. */
    private static volatile ExecutorService executor;

    private DroneScoring() {
    }

    private static ExecutorService executor() {
        ExecutorService local = executor;
        if (local == null) {
            synchronized (DroneScoring.class) {
                local = executor;
                if (local == null) {
                    local = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "Drone Scorer");
                        t.setDaemon(true);
                        return t;
                    });
                    executor = local;
                }
            }
        }
        return local;
    }

    /**
     * Run {@code graph} once on the standardized field with {@code seed} for the full tick budget (or until
     * it finishes/halts) and return its score. Pure and deterministic — safe to call from any thread and
     * directly testable. The graph is consumed by the run; pass a fresh graph per call.
     */
    public static int scoreOnce(DroneGraph graph, long seed) {
        FarmSimulation sim = FarmSimulation.allFertile(DroneField.WIDTH, DroneField.HEIGHT, FarmConfig.DEFAULT, seed);
        DroneApi api = new DroneApi(sim, DroneField.START_X, DroneField.START_Z);
        DroneRuntime rt = new DroneRuntime(graph, api, seed);
        for (int t = 0; t < TICK_BUDGET && !rt.isFinished() && !rt.isHalted(); t++) {
            rt.tick(t);
        }
        return rt.getScore();
    }

    /**
     * The official score: run the program once per seed in {@link #SCORING_SEEDS} and take the median.
     * {@code graphFactory} must supply a <em>fresh</em> graph for each run (a run consumes its graph), so the
     * runs never share mutable state. Pure and deterministic given a deterministic factory.
     */
    public static int scoreMedian(Supplier<DroneGraph> graphFactory) {
        int[] scores = new int[SCORING_SEEDS.length];
        for (int i = 0; i < SCORING_SEEDS.length; i++) {
            scores[i] = scoreOnce(graphFactory.get(), SCORING_SEEDS[i]);
        }
        Arrays.sort(scores);
        return scores[scores.length / 2]; // median (SCORING_SEEDS has an odd length)
    }

    /**
     * Score {@code program} on a background thread (median over {@link #SCORING_SEEDS}), then record the
     * result for {@code owner} on the main server thread and notify them if they are online. A fresh graph is
     * deserialized per seed from {@code program} using {@code provider}; the provider is an immutable registry
     * snapshot, so the off-thread deserialization is safe and no mutable graph is shared between runs.
     */
    public static void submitAsync(MinecraftServer server, UUID owner, String name,
                                   CompoundTag program, HolderLookup.Provider provider) {
        executor().submit(() -> {
            try {
                int score = scoreMedian(() -> DroneGraphCodec.fromTag(program, provider));
                if (!Platform.serverSafe(server)) return;
                server.executeIfPossible(() -> {
                    if (!Platform.serverSafe(server)) return;
                    DroneLeaderboard.get(server.overworld()).recordScore(owner, name, score, TICK_BUDGET);
                    ServerPlayer player = server.getPlayerList().getPlayer(owner);
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable(
                                "message.kilagraphdemo.drone_scored", score, SCORING_SEEDS.length));
                    }
                });
            } catch (Throwable e) {
                LOGGER.error("[KilaGraphDemo] drone scoring failed for {}", owner, e);
            }
        });
    }
}
