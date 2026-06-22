package com.lowdragmc.kilagraphdemo.mixin;

import com.lowdragmc.kilagraphdemo.client.drone.CameraOverrideManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the hotbar (the player's item bar) while the drone station's override camera is active, so it does
 * not float over the top-down farm view shown through the transparent half of the menu. Only the hotbar
 * layer is suppressed — chat, health and the rest of the HUD are left intact.
 */
@Mixin(Gui.class)
public class HotbarHudMixin {

    @Inject(method = "extractHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void kilagraphdemo$hideHotbarForDroneView(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                                      CallbackInfo ci) {
        if (CameraOverrideManager.INSTANCE.isActive()) {
            ci.cancel();
        }
    }
}
