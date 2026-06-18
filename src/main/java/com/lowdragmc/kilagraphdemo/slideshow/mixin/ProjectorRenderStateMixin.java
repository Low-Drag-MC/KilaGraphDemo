package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.lowdragmc.kilagraphdemo.slideshow.IProjectorRenderStateExt;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.teacon.slides.renderer.ProjectorRenderState;

/**
 * Carries the selected graph-work uid on SlideShow's per-frame projector render state, so it survives from
 * {@code extractRenderState} (which sees the block entity) to {@code submit} (which sees only the state).
 * Empty/null = render vanilla.
 */
@Mixin(ProjectorRenderState.class)
public class ProjectorRenderStateMixin implements IProjectorRenderStateExt {

    @Unique
    @Nullable
    private String kilagraphdemo$graphUid;

    @Override
    @Nullable
    public String kilagraphdemo$getGraphUid() {
        return kilagraphdemo$graphUid;
    }

    @Override
    public void kilagraphdemo$setGraphUid(@Nullable String uid) {
        this.kilagraphdemo$graphUid = uid;
    }
}
