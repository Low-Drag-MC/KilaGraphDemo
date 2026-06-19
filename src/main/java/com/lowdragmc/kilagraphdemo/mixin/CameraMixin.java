package com.lowdragmc.kilagraphdemo.mixin;

import com.lowdragmc.kilagraphdemo.client.drone.CameraOverrideManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the render camera while {@link CameraOverrideManager} is active, letting the drone station UI
 * fly a free camera over the farm. Only the render camera is moved, not the player entity.
 *
 * <p>Injected right after {@code Camera#alignWithEntity} (which is where the vanilla code sets the camera's
 * position/rotation from the player) inside {@code update(DeltaTracker)} — before the cull frustum is built
 * from that position, so the frustum matches our overridden vantage and nothing is wrongly culled. (Note:
 * in 26.1 the per-frame method is {@code update}, not the old {@code setup}.)</p>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER))
    private void kilagraphdemo$overrideCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        CameraOverrideManager mgr = CameraOverrideManager.INSTANCE;
        if (!mgr.isActive()) return;
        // Safety net: the UI is a Screen, so if none is open the UI has closed — drop the override
        // rather than risk a stuck camera the player can't recover from.
        if (Minecraft.getInstance().screen == null) {
            mgr.deactivate();
            return;
        }
        setPosition(mgr.pos());
        setRotation(mgr.yaw(), mgr.pitch());
    }

    /**
     * Replace the snapshotted projection with a <em>centered</em> orthographic one while the override is
     * active, so the farm renders without perspective foreshortening. Vanilla's own {@code setupOrtho} is
     * corner-origin (for HUDs), so we build a centered volume here instead. The cull frustum stays
     * perspective-based — acceptable for the small top-down view.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/renderer/state/level/CameraRenderState;F)V",
            at = @At("TAIL"))
    private void kilagraphdemo$orthoProjection(CameraRenderState state, float partialTick, CallbackInfo ci) {
        CameraOverrideManager mgr = CameraOverrideManager.INSTANCE;
        if (!mgr.isActive() || Minecraft.getInstance().screen == null) return;
        var window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        float h = mgr.orthoHeight();
        float w = h * aspect;
        boolean zZeroToOne = RenderSystem.getDevice().isZZeroToOne();
        state.projectionMatrix.identity().setOrtho(-w / 2f, w / 2f, -h / 2f, h / 2f, -1000f, 1000f, zZeroToOne);
    }
}
