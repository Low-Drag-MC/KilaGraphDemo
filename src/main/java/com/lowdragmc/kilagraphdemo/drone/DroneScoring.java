package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.farm.FarmConfig;
import com.lowdragmc.kilagraphdemo.farm.FarmSimulation;
import com.lowdragmc.kilagraphdemo.server.DroneLeaderboard;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Official, fair scoring of a submitted drone program. Every submission is scored on the same
 * <b>fixed virtual field</b> ({@link DroneField}, the same one the in-world preview uses) with the fixed
 * {@link DroneStationBlockEntity#RUN_SEED} and a fixed tick budget, so the leaderboard compares pure
 * programming skill.
 *
 * <p>The simulation is pure Java and single-threaded per instance ({@link DroneRuntime}), so a run is
 * dispatched to a background daemon thread; only the final {@link DroneLeaderboard#recordScore write-back}
 * is hopped back onto the main server thread via {@link MinecraftServer#executeIfPossible}.</p>
 */
public final class DroneScoring {

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
     * Run {@code graph} on the standardized field for the full tick budget (or until it finishes/halts)
     * and return its score. Pure and deterministic — safe to call from any thread and directly testable.
     */
    public static int scoreSync(DroneGraph graph) {
        FarmSimulation sim = FarmSimulation.allFertile(DroneField.WIDTH, DroneField.HEIGHT, FarmConfig.DEFAULT,
                DroneStationBlockEntity.RUN_SEED);
        DroneApi api = new DroneApi(sim, DroneField.START_X, DroneField.START_Z);
        DroneRuntime rt = new DroneRuntime(graph, api, DroneStationBlockEntity.RUN_SEED);
        for (int t = 0; t < TICK_BUDGET && !rt.isFinished() && !rt.isHalted(); t++) {
            rt.tick(t);
        }
        return rt.getScore();
    }

    /**
     * Score {@code graph} on a background thread, then record the result for {@code owner} on the main
     * server thread and notify them if they are online. The graph must already be deserialized on the
     * main thread (it is owned solely by the worker afterwards, so there is no shared mutable state).
     */
    public static void submitAsync(MinecraftServer server, UUID owner, String name, DroneGraph graph) {
        executor().submit(() -> {
            try {
                int score = scoreSync(graph);
                server.executeIfPossible(() -> {
                    DroneLeaderboard.get(server.overworld()).recordScore(owner, name, score, TICK_BUDGET);
                    ServerPlayer player = server.getPlayerList().getPlayer(owner);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal(
                                "Drone solution scored: " + score + " points (" + TICK_BUDGET + " ticks)."));
                    }
                });
            } catch (Throwable e) {
                LOGGER.error("[KilaGraphDemo] drone scoring failed for {}", owner, e);
            }
        });
    }
}
