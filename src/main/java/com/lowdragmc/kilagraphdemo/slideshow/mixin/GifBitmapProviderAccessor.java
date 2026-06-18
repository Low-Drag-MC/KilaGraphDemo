package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.teacon.slides.renderer.bitmap.GIFBitmapProvider;

/**
 * Exposes the (stable) {@link GpuTexture} held by SlideShow's {@code GIFBitmapProvider}. The texture object
 * is fixed for the provider's lifetime; its contents are rewritten per animation frame in
 * {@code updateAndGet}, so binding the view once still shows the current frame.
 */
@Mixin(GIFBitmapProvider.class)
public interface GifBitmapProviderAccessor {

    @Accessor("mTexture")
    GpuTexture kilagraphdemo$getTexture();
}
