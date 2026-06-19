package com.lowdragmc.kilagraphdemo.farm;

/**
 * Tunable, deterministic constants for the farm simulation.
 *
 * @param growTicks     ticks from planting until a pumpkin becomes {@link Stage#RIPE}
 * @param freshTicks    ticks a ripe pumpkin stays fresh before it rots
 * @param maxMergeSize  largest square block that can merge (N x N), capped at this N
 */
public record FarmConfig(int growTicks, int freshTicks, int maxMergeSize) {
    public static final FarmConfig DEFAULT = new FarmConfig(600, 400, 4);
}
