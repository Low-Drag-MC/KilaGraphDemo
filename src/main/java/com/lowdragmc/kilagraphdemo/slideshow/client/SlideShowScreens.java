package com.lowdragmc.kilagraphdemo.slideshow.client;

import com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraph;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Client-only entry points for the SlideShow projector screens. Opening one replaces the current screen
 * (the projector's container menu closes — LDLib2 has no screen-layer API), which is why the browser
 * captures the projector {@code BlockPos} up front and applies its selection via a packet, like the server
 * hologram flow. Must only be called on the client.
 */
public final class SlideShowScreens {

    private SlideShowScreens() {}

    /** Open the projector graph browser (choose / manage / upload) for the projector at {@code pos}. */
    public static void openBrowse(Level level, BlockPos pos) {
        var ui = new SlideShowProjectorUI(GlobalPos.of(level.dimension(), pos));
        setScreen(ui.getRoot());
    }

    /** Open the graph editor on {@code graph}; {@code onSaved} receives the graph tag + bundled deps on save. */
    public static void openEditor(SlideShowGraph graph, SlideShowEditorWindow.SaveCallback onSaved) {
        var handle = SlideShowEditorWindow.build(graph, onSaved);
        var modularUI = new ModularUI(UI.of(handle.root, StylesheetManager.ORE_MERGED));
        Minecraft.getInstance().setScreen(new SlideShowEditorScreen(modularUI, handle));
    }

    private static void setScreen(UIElement root) {
        var modularUI = new ModularUI(UI.of(root, StylesheetManager.ORE_MERGED));
        Minecraft.getInstance().setScreen(new ModularUIScreen(modularUI, Component.empty()));
    }
}
