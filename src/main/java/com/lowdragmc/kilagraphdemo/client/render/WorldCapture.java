package com.lowdragmc.kilagraphdemo.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Captures the main render target's colour into an owned texture so it can be shown as a live "world
 * view" in a UI panel. Modelled on KilaGraph's {@code SceneCaptureManager}: {@link #capture()} copies
 * the main framebuffer (called at {@code RenderLevelStageEvent.AfterLevel} — world done, hand/GUI not
 * yet); ref-counted {@link #acquire()}/{@link #release()} mean the texture only exists (and capture only
 * runs) while a panel is open. All methods run on the render thread.
 */
public final class WorldCapture {

    public static final WorldCapture INSTANCE = new WorldCapture();

    // COPY_DST(1) | TEXTURE_BINDING(4): we only copy into it and sample it.
    private static final int USAGE = 1 | 4;

    private int users;
    private int width, height;
    @Nullable private TextureFormat format;
    @Nullable private GpuTexture colorTexture;
    @Nullable private GpuTextureView colorView;
    @Nullable private GpuSampler sampler;

    private WorldCapture() {
    }

    public void acquire() {
        users++;
    }

    public void release() {
        if (users > 0) users--;
        if (users == 0) destroy();
    }

    /** Copy the main framebuffer colour into our owned texture. No-op when no panel needs it. */
    public void capture() {
        if (users <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        GpuTexture mainColor = main.getColorTexture();
        if (mainColor == null) return;
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0) return;
        ensure(w, h, mainColor.getFormat());
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainColor, colorTexture, 0, 0, 0, 0, 0, w, h);
    }

    @Nullable
    public GpuTextureView colorView() {
        return colorView;
    }

    /** Captured texture width (= main framebuffer width), or 0 before the first capture. */
    public int width() {
        return colorTexture == null ? 0 : width;
    }

    /** Captured texture height (= main framebuffer height), or 0 before the first capture. */
    public int height() {
        return colorTexture == null ? 0 : height;
    }

    public GpuSampler sampler() {
        if (sampler == null) {
            sampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
        return sampler;
    }

    private void ensure(int w, int h, TextureFormat f) {
        if (colorTexture != null && w == width && h == height && f == format) return;
        destroyTextures();
        var device = RenderSystem.getDevice();
        width = w;
        height = h;
        format = f;
        colorTexture = device.createTexture(() -> "KilaGraphDemo/WorldView", USAGE, f, w, h, 1, 1);
        colorView = device.createTextureView(colorTexture);
    }

    public void destroy() {
        destroyTextures();
    }

    private void destroyTextures() {
        if (colorView != null) { colorView.close(); colorView = null; }
        if (colorTexture != null) { colorTexture.close(); colorTexture = null; }
        width = height = 0;
        format = null;
    }
}
