package com.lowdragmc.kilagraphdemo.client.drone;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;

/**
 * Per-frame render snapshot for a drone station: the synced field grid + drone cell, copied from the
 * client {@link com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity} each frame so
 * {@link DroneStationRenderer#submit} can draw the pumpkins and the hovering drone.
 */
public class DroneStationRenderState extends BlockEntityRenderState {
    public BlockPos pos = BlockPos.ZERO;
    public int fieldWidth;
    public int fieldHeight;
    public int fieldOffX;
    public int fieldOffZ;
    /** One int per cell: {@code stageOrdinal | (mergeSize << 8)}. Empty when no run is active. */
    public int[] cells = new int[0];
    public int droneX;
    public int droneZ;
    /** Whether a run is active (field populated) — drives whether the drone flies the field or parks. */
    public boolean active;
    /** Smooth time (game time + partial tick) for the drone's hover bob. */
    public float time;
}
