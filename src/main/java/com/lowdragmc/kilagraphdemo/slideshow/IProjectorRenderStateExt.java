package com.lowdragmc.kilagraphdemo.slideshow;

import org.jetbrains.annotations.Nullable;

/**
 * Duck-typing interface mixed into SlideShow's {@code ProjectorRenderState} to carry the selected
 * graph-work uid from {@code extractRenderState} (which has the block entity) through to {@code submit}
 * (which has only the render state). Empty/null means "render vanilla" — no graph override.
 */
public interface IProjectorRenderStateExt {

    @Nullable
    String kilagraphdemo$getGraphUid();

    void kilagraphdemo$setGraphUid(@Nullable String uid);
}
