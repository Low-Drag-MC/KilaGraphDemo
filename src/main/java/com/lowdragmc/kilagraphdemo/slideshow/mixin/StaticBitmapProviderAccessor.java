package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.teacon.slides.renderer.bitmap.StaticBitmapProvider;

/**
 * Exposes the decoded slide {@link GpuTexture} held by SlideShow's {@code StaticBitmapProvider}, so the
 * render hook can bind it into a {@link com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraph}'s sampler.
 */
@Mixin(StaticBitmapProvider.class)
public interface StaticBitmapProviderAccessor {

    @Accessor("mTexture")
    GpuTexture kilagraphdemo$getTexture();
}
