package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.lowdragmc.kilagraphdemo.slideshow.IProjectorGraphHolder;
import com.lowdragmc.kilagraphdemo.slideshow.IProjectorRenderStateExt;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.renderer.ProjectorRenderState;
import org.teacon.slides.renderer.ProjectorRenderer;

/**
 * Carries the projector's selected graph-work uid from state extraction (which sees the block entity) to the
 * slide-sequence render. {@code extractRenderState} stashes the uid on the render state; {@code submit}
 * publishes it to {@link SlideShowRenderContext} for the duration of the render, where the texture redirect
 * resolves the per-image material.
 */
@Mixin(ProjectorRenderer.class)
public class ProjectorRendererMixin {

    @Inject(method = "extractRenderState(Lorg/teacon/slides/block/ProjectorBlockEntity;Lorg/teacon/slides/renderer/ProjectorRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("TAIL"))
    private void kilagraphdemo$stashUid(ProjectorBlockEntity be, ProjectorRenderState state,
                                        float partialTick, Vec3 cameraPos,
                                        ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        // ProjectorBlockEntity / ProjectorRenderState are final; cast through Object so javac allows the
        // mixin-injected interfaces (the runtime types do implement them).
        String uid = (Object) be instanceof IProjectorGraphHolder holder
                ? holder.kilagraphdemo$getGraphWorkUid() : null;
        ((IProjectorRenderStateExt) (Object) state).kilagraphdemo$setGraphUid(uid);
    }

    @Inject(method = "submit(Lorg/teacon/slides/renderer/ProjectorRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void kilagraphdemo$beginSubmit(ProjectorRenderState state, PoseStack pose,
                                           SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        SlideShowRenderContext.begin(((IProjectorRenderStateExt) (Object) state).kilagraphdemo$getGraphUid());
    }

    @Inject(method = "submit(Lorg/teacon/slides/renderer/ProjectorRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void kilagraphdemo$endSubmit(ProjectorRenderState state, PoseStack pose,
                                         SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        SlideShowRenderContext.end();
    }
}
