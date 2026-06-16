package com.lowdragmc.kilagraphdemo.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * Render state for the hologram block entity. The base {@code blockState} field is private, so we
 * capture the mounting face (and the block's global position, used to look up which graph this
 * hologram displays) here during extraction for use on the render thread.
 */
public class HologramRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.UP;
    /** Identifies this hologram for {@code HologramDisplays} override lookup; null if level unavailable. */
    @Nullable
    public GlobalPos posKey;
    /** Slow spin (degrees) of the projected model, for a living hologram look. */
    public float spinDegrees;
}
