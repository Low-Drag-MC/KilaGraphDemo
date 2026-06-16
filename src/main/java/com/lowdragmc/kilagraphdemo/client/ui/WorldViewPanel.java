package com.lowdragmc.kilagraphdemo.client.ui;

import com.lowdragmc.kilagraphdemo.client.render.WorldCapture;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.renderstate.FloatBlitRenderState;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

/**
 * A live view of the player's real world — blits {@link WorldCapture}'s captured framebuffer into the
 * panel. Capture is ref-counted to this panel being mounted ({@code onAdded}/{@code onRemoved}), so it
 * only runs (and only allocates a texture) while the editor is open.
 */
public class WorldViewPanel extends View {

    public WorldViewPanel() {
        setName("World");
        getLayout().widthPercent(100).heightPercent(100);
    }

    @Override
    protected void onAdded() {
        super.onAdded();
        WorldCapture.INSTANCE.acquire();
    }

    @Override
    protected void onRemoved() {
        super.onRemoved();
        WorldCapture.INSTANCE.release();
    }

    @LDLRegisterClient(name = "kilagraphdemo_world_view", registry = "ldlib2:ui_element_renderer")
    public static final class Renderer extends DelegatingUIElementRenderer<WorldViewPanel, Renderer> {
        @Override
        public Class<WorldViewPanel> type() {
            return WorldViewPanel.class;
        }

        @Override
        public void drawBackgroundAdditional(WorldViewPanel panel, IGUIContext context) {
            if (!(context instanceof GUIContext gui)) {
                drawParentBackgroundAdditional(panel, context);
                return;
            }
            GpuTextureView view = WorldCapture.INSTANCE.colorView();
            int texW = WorldCapture.INSTANCE.width();
            int texH = WorldCapture.INSTANCE.height();
            if (view == null || texW <= 0 || texH <= 0) return; // not captured yet
            float x = panel.getContentX();
            float y = panel.getContentY();
            float w = panel.getContentWidth();
            float h = panel.getPaddingHeight();
            // Preserve the screen aspect ratio: fit the image inside the panel (one edge = 100%,
            // centred on the other axis) so it isn't stretched.
            float texAspect = (float) texW / texH;
            float dw = w, dh = h;
            if (w / h > texAspect) {
                dh = h;
                dw = h * texAspect;
            } else {
                dw = w;
                dh = w / texAspect;
            }
            float dx = x + (w - dw) / 2f;
            float dy = y + (h - dh) / 2f;
            // Framebuffer origin is bottom-left; flip V so the image is upright in the (top-left) GUI.
            gui.addGuiElement(new FloatBlitRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.singleTexture(view, WorldCapture.INSTANCE.sampler()),
                    new Matrix3x2f(gui.currentPose()),
                    dx, dy, dx + dw, dy + dh,
                    0f, 1f, 1f, 0f,
                    -1, null));
        }
    }
}
