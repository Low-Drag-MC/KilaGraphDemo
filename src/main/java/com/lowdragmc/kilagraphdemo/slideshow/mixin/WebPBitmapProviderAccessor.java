package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.teacon.slides.renderer.bitmap.WebPBitmapProvider;

/**
 * Exposes the (stable) {@link GpuTexture} held by SlideShow's {@code WebPBitmapProvider}. As with the GIF
 * provider, the texture object is fixed for the provider's lifetime and its contents are rewritten per
 * animation frame, so binding the view once shows the current frame.
 */
@Mixin(WebPBitmapProvider.class)
public interface WebPBitmapProviderAccessor {

    @Accessor("mTexture")
    GpuTexture kilagraphdemo$getTexture();
}
