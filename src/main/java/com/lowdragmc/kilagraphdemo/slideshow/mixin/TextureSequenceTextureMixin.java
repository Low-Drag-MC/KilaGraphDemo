package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowRenderContext;
import com.lowdragmc.kilagraphdemo.slideshow.client.ClientProjectorGraphs;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.teacon.slides.renderer.bitmap.BitmapProvider;
import org.teacon.slides.renderer.bitmap.GIFBitmapProvider;
import org.teacon.slides.renderer.bitmap.StaticBitmapProvider;
import org.teacon.slides.renderer.bitmap.WebPBitmapProvider;

/**
 * The actual RenderType swap: in {@code TextureSequence$Texture.render}, the slide's RenderType comes from
 * {@code provider.updateAndGet(tick, partial)}. We still call that (it advances animated frames + rewrites the
 * provider's texture), then — when a {@code SlideShowGraph} is selected for this projector (uid published by
 * {@code ProjectorRendererMixin} via {@link SlideShowRenderContext}) — resolve the per-(graph, image) material
 * and return its RenderType so the slide draws through the graph shader. Static, GIF and WebP providers are all
 * supported (each exposes a stable {@link GpuTexture}); the no-override case falls through to vanilla.
 *
 * <p>Materials are keyed by (graph uid, slide texture) in {@link ClientProjectorGraphs}, so two projectors
 * showing the same image through the same graph share one material/RenderType (and different images don't
 * alias).</p>
 */
@Mixin(targets = "org.teacon.slides.renderer.TextureSequence$Texture")
public class TextureSequenceTextureMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lorg/teacon/slides/renderer/bitmap/BitmapProvider;updateAndGet(JF)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType kilagraphdemo$overrideRenderType(BitmapProvider provider, long tick, float partial) {
        RenderType original = provider.updateAndGet(tick, partial); // advances animation + updates the texture
        String uid = SlideShowRenderContext.currentUid();
        if (uid != null && !uid.isEmpty()) {
            GpuTexture texture = kilagraphdemo$textureOf(provider);
            if (texture != null) {
                RenderTypeGraphMaterial material = ClientProjectorGraphs.getMaterial(uid, texture);
                if (material != null) return material.renderType();
            }
        }
        return original;
    }

    /** The stable {@link GpuTexture} backing a supported slide provider, or null for unsupported kinds. */
    @Unique
    @Nullable
    private static GpuTexture kilagraphdemo$textureOf(BitmapProvider provider) {
        if (provider instanceof StaticBitmapProvider) {
            return ((StaticBitmapProviderAccessor) provider).kilagraphdemo$getTexture();
        }
        if (provider instanceof GIFBitmapProvider) {
            return ((GifBitmapProviderAccessor) provider).kilagraphdemo$getTexture();
        }
        if (provider instanceof WebPBitmapProvider) {
            return ((WebPBitmapProviderAccessor) provider).kilagraphdemo$getTexture();
        }
        return null;
    }
}
