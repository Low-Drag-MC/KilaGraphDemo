package com.lowdragmc.kilagraphdemo.client;

import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Render state for a {@link com.lowdragmc.kilagraphdemo.block.ServerHologramBlockEntity}. The displayed
 * work (resolved from the synced uid via {@link com.lowdragmc.kilagraphdemo.client.render.ServerHologramDisplays})
 * and its download progress are captured during extraction for use on the render thread.
 */
public class ServerHologramRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.UP;
    public float spinDegrees;
    /** The per-block placement (transform + spin), synced from the block entity. */
    public HologramPlacement placement = HologramPlacement.DEFAULT;
    /** The resolved display, or null while the work is missing/downloading. */
    @Nullable
    public HologramDisplay display;
    /** Download progress in 0..1 when {@link #display} is null and a uid is set; otherwise unused. */
    public float progress;
    /** Whether the block has a (non-empty) work uid assigned — drives the progress placeholder. */
    public boolean hasUid;
}
