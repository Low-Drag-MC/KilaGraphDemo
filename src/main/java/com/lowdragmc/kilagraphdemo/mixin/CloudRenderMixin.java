package com.lowdragmc.kilagraphdemo.mixin;

import com.lowdragmc.kilagraphdemo.client.drone.CameraOverrideManager;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses cloud rendering while the drone station's override camera is active: the top-down farm view
 * looks straight up through the cloud layer, which would otherwise blank out the board.
 */
@Mixin(CloudRenderer.class)
public class CloudRenderMixin {

    @Inject(method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"), cancellable = true)
    private void kilagraphdemo$hideCloudsForDroneView(int color, CloudStatus status, float height, int renderDistance,
                                                      Vec3 cameraPos, long ticks, float partialTick, CallbackInfo ci) {
        if (CameraOverrideManager.INSTANCE.isActive()) {
            ci.cancel();
        }
    }
}
