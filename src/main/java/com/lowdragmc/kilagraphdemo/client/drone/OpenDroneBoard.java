package com.lowdragmc.kilagraphdemo.client.drone;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only pointer to the drone board (station or ranking block) whose menu is currently open, or
 * {@code null}. Read by {@link DroneStationRenderer} to overlay the field's coordinate labels only for the
 * board the player is currently looking at; set/cleared by the board's client UI when its screen opens/closes.
 */
public final class OpenDroneBoard {
    private OpenDroneBoard() {
    }

    @Nullable
    public static volatile BlockPos pos;
}
