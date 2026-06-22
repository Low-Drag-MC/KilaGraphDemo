package com.lowdragmc.kilagraphdemo.mixin;

import com.lowdragmc.kilagraphdemo.client.drone.CameraOverrideManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the first-person hand/held item while the drone station's override camera is active: the top-down
 * farm view would otherwise show the player's floating arm across the board.
 */
@Mixin(ItemInHandRenderer.class)
public class HandRenderMixin {

    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void kilagraphdemo$hideHandForDroneView(float frameInterp, PoseStack poseStack,
                                                    SubmitNodeCollector submitNodeCollector, LocalPlayer player,
                                                    int lightCoords, CallbackInfo ci) {
        if (CameraOverrideManager.INSTANCE.isActive()) {
            ci.cancel();
        }
    }
}
