package com.lowdragmc.kilagraphdemo.farm;

/**
 * Tunable constants for the farm simulation. Growth and rot durations are <em>per-pumpkin random</em>:
 * each pumpkin's actual grow/fresh time is drawn from a Gaussian centred on the mean with the given
 * standard deviation (clamped to a sensible range), so a field doesn't ripen all at once. The draws are
 * seeded ({@link FarmSimulation}), so a program still scores identically every run — fair leaderboard.
 *
 * @param growTicks     mean ticks from planting until a pumpkin becomes {@link Stage#RIPE}
 * @param freshTicks    mean ticks a ripe pumpkin stays fresh before it rots
 * @param maxMergeSize  largest square block that can merge (N x N), capped at this N
 * @param growJitter    standard deviation of the grow time (0 = fixed/deterministic timing)
 * @param rotJitter     standard deviation of the fresh-before-rot time (0 = fixed/deterministic timing)
 */
public record FarmConfig(int growTicks, int freshTicks, int maxMergeSize, int growJitter, int rotJitter) {

    /** Fixed-timing config (no jitter); handy for tests that assert exact ripening ticks. */
    public FarmConfig(int growTicks, int freshTicks, int maxMergeSize) {
        this(growTicks, freshTicks, maxMergeSize, 0, 0);
    }

    public static final FarmConfig DEFAULT = new FarmConfig(600, 400, 4, 120, 100);
}
