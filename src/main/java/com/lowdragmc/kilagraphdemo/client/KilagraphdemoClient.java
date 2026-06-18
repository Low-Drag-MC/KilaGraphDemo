package com.lowdragmc.kilagraphdemo.client;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.client.render.HologramAabbOverlay;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplays;
import com.lowdragmc.kilagraphdemo.client.render.HologramPlacements;
import com.lowdragmc.kilagraphdemo.client.render.ServerHologramDisplays;
import com.lowdragmc.kilagraphdemo.client.render.WorldCapture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Client-side event handling: registers the hologram block entity renderer (mod bus) and drops the
 * ephemeral runtime hologram displays on disconnect (game bus). NeoForge routes each handler to the
 * correct bus by event type.
 */
@EventBusSubscriber(modid = Kilagraphdemo.MODID, value = Dist.CLIENT)
public final class KilagraphdemoClient {
    private KilagraphdemoClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.HOLOGRAM_BE.get(), HologramRenderer::new);
        event.registerBlockEntityRenderer(ModRegistries.SERVER_HOLOGRAM_BE.get(), ServerHologramRenderer::new);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Runtime display overrides are ephemeral — discard them so holograms revert to the default.
        HologramDisplays.clearAll();
        HologramPlacements.clearAll();
        HologramAabbOverlay.clearAll();
        ServerHologramDisplays.clearAll();
        // Free the world-view capture texture if the editor was open at disconnect (no more capture frames
        // to flush its deferred close).
        WorldCapture.INSTANCE.destroy();
    }

    @SubscribeEvent
    public static void onRenderLevelAfter(RenderLevelStageEvent.AfterLevel event) {
        // World done, hand/overlay/GUI not yet — grab the frame for the editor's live world view.
        // No-op unless a WorldViewPanel is open (ref-counted in WorldCapture).
        WorldCapture.INSTANCE.capture();
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        // Draw any active "Display AABB" debug wireframes into the world. No-op when none are active.
        HologramAabbOverlay.render(event.getSubmitNodeCollector(), event.getPoseStack());
    }
}
