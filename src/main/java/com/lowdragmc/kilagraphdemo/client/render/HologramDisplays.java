package com.lowdragmc.kilagraphdemo.client.render;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side registry of hologram displays. Every hologram falls back to a single shared
 * {@link #getDefault() default} (so they reuse one material); a player may set a per-block runtime
 * override which is <b>ephemeral</b> — not persisted and dropped on level unload / disconnect
 * ({@link #clearAll()}), so blocks revert to the default on reload.
 */
public final class HologramDisplays {

    private static final Map<GlobalPos, HologramDisplay> OVERRIDES = new HashMap<>();
    @Nullable
    private static HologramDisplay defaultDisplay;

    private HologramDisplays() {
    }

    /** The shared default hologram content (a plain default RenderTypeGraph on a cube). */
    public static HologramDisplay getDefault() {
        if (defaultDisplay == null) {
            defaultDisplay = new HologramDisplay(new RenderTypeGraph(), KGPreviewContents.CUBE);
        }
        return defaultDisplay;
    }

    /** The display a hologram at {@code pos} should render: its runtime override, else the default. */
    public static HologramDisplay resolve(GlobalPos pos) {
        HologramDisplay override = OVERRIDES.get(pos);
        return override != null ? override : getDefault();
    }

    public static void setOverride(GlobalPos pos, HologramDisplay display) {
        HologramDisplay previous = OVERRIDES.put(pos, display);
        if (previous != null && previous != display) previous.close();
    }

    public static void clearOverride(GlobalPos pos) {
        HologramDisplay removed = OVERRIDES.remove(pos);
        if (removed != null) removed.close();
    }

    /** Drop all runtime overrides (level unload / disconnect). The default is kept (cheap, reusable). */
    public static void clearAll() {
        OVERRIDES.values().forEach(HologramDisplay::close);
        OVERRIDES.clear();
    }
}
