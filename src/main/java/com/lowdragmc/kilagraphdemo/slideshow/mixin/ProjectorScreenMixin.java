package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.lowdragmc.kilagraphdemo.slideshow.client.SlideShowScreens;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIClientAccess;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.teacon.slides.inventory.ProjectorContainerMenu;
import org.teacon.slides.screen.ProjectorScreen;

/**
 * Adds two creative-only buttons to SlideShow's projector screen: "Graph…" opens the SlideShow graph
 * browser (choose / manage / upload), positioned just right of the projector GUI. Opening it replaces the
 * container screen (LDLib2 has no overlay API), so the browser carries the projector {@code BlockPos} and
 * applies the chosen graph via a packet. Non-creative players see no buttons.
 *
 * <p>Declared {@code extends AbstractContainerScreen} purely so the inherited {@code addRenderableWidget} /
 * {@code getMenu} / layout fields are callable from the injected method; the dummy constructor is never
 * applied by Mixin.</p>
 */
@Mixin(ProjectorScreen.class)
public abstract class ProjectorScreenMixin extends AbstractContainerScreen<ProjectorContainerMenu> {

    private ProjectorScreenMixin() {
        super(null, null, null); // never invoked — required by javac because the superclass has no no-arg ctor
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void kilagraphdemo$addGraphButton(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isCreative()) return;
        BlockPos pos = getMenu().tilePos;
//        Button button = Button.builder(Component.literal("KilaGraph"), b -> {
//
//        }).bounds(this.leftPos + this.imageWidth + 4, this.topPos, 56, 20).build();
        var modular = new ModularUI(UI.of(new UIElement().layout(l -> l.width(this.imageWidth).height(this.imageHeight))
                .addChild(new UIElement()
                        .layout(l -> l.width(18).height(19)
                                .left(this.imageWidth + 4)
                                .top(4))
                        .style(s -> s.background(SpriteTexture.of("kilagraphdemo:textures/gui/kg_button.png"))
                                .tooltips("KilaGraph Shader"))
                        .addEventListener(UIEvents.CLICK, e -> {
                            Level level = player.level();
                            SlideShowScreens.openBrowse(level, pos);
                        })
        )));
        ModularUIClientAccess.setScreenAndInit(modular, this);
        addRenderableWidget(ModularUIClientAccess.getWidget(modular));
    }
}
