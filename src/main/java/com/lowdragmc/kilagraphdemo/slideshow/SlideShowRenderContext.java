package com.lowdragmc.kilagraphdemo.slideshow;

import org.jetbrains.annotations.Nullable;

/**
 * Render-thread plumbing for the SlideShow graph override. {@code ProjectorRenderer.submit} publishes the
 * projector's selected graph-work uid here for the duration of the slide-sequence render; the
 * {@code TextureSequence$Texture.render} redirect reads it (together with the slide's {@code GpuTexture}) to
 * resolve the per-image material from {@link com.lowdragmc.kilagraphdemo.slideshow.client.ClientProjectorGraphs}.
 *
 * <p>All access is on the render thread during the BER submit phase (synchronous), so a simple static holder
 * suffices.</p>
 */
public final class SlideShowRenderContext {

    @Nullable
    private static String currentUid;

    private SlideShowRenderContext() {}

    /** Publish the graph-work uid active for the slide sequence about to render (null/empty = render vanilla). */
    public static void begin(@Nullable String uid) {
        currentUid = uid;
    }

    public static void end() {
        currentUid = null;
    }

    @Nullable
    public static String currentUid() {
        return currentUid;
    }
}
