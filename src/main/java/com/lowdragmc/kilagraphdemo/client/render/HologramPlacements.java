package com.lowdragmc.kilagraphdemo.client.render;

import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import net.minecraft.core.GlobalPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side per-block hologram {@link HologramPlacement} overrides for the <b>regular</b> hologram. Like
 * {@link HologramDisplays}, these are <b>ephemeral</b> — runtime-only, not persisted, dropped on level unload /
 * disconnect ({@link #clearAll()}), so a block reverts to {@link HologramPlacement#DEFAULT} on reload. (The
 * server hologram instead persists + syncs its placement on the block entity.)
 */
public final class HologramPlacements {

    private static final Map<GlobalPos, HologramPlacement> OVERRIDES = new HashMap<>();

    private HologramPlacements() {
    }

    /** The placement for the hologram at {@code pos}: its runtime override, else the default. */
    public static HologramPlacement resolve(GlobalPos pos) {
        return OVERRIDES.getOrDefault(pos, HologramPlacement.DEFAULT);
    }

    public static void set(GlobalPos pos, HologramPlacement placement) {
        OVERRIDES.put(pos, placement);
    }

    public static void clearAll() {
        OVERRIDES.clear();
    }
}
