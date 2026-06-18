package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates the SlideShow-compat mixins on the SlideShow mod ({@code slide_show}) being present. When it is
 * absent every mixin in {@code kilagraphdemo.slideshow.mixins.json} is skipped, so the mod loads and runs
 * normally without SlideShow (the {@code org.teacon.slides.*} target classes never load either, so nothing
 * here is ever triggered). Checked at mixin-prepare time via {@link LoadingModList}.
 */
public class SlideShowMixinPlugin implements IMixinConfigPlugin {

    public static final String SLIDESHOW_MODID = "slide_show";

    @SuppressWarnings("removal") // LoadingModList.get() is the only entry point available this early in mixin bootstrap
    private static boolean slideShowPresent() {
        var list = LoadingModList.get();
        return list != null && list.getModFileById(SLIDESHOW_MODID) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return slideShowPresent();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
