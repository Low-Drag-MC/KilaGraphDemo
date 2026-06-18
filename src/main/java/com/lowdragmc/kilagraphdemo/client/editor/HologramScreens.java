package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.ui.HologramBrowseUI;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Client-only entry points that open the hologram's modular-UI screens. Must only be called on the
 * client (guarded by {@code level.isClientSide} at the call site); referencing it elsewhere on a
 * dedicated server would classload client-only types.
 */
public final class HologramScreens {

    private HologramScreens() {
    }

    /** Open the local-works browser for the hologram at {@code pos}. */
    public static void openBrowse(Level level, BlockPos pos) {
        var be = level.getBlockEntity(pos) instanceof com.lowdragmc.kilagraphdemo.block.HologramBlockEntity h ? h : null;
        var ui = new HologramBrowseUI(GlobalPos.of(level.dimension(), pos), be);
        setScreen(ui.getRoot());
    }

    /** Open the server-works browser for the server hologram at {@code pos}. */
    public static void openServerBrowse(Level level, BlockPos pos) {
        var be = level.getBlockEntity(pos) instanceof com.lowdragmc.kilagraphdemo.block.ServerHologramBlockEntity h ? h : null;
        var ui = new com.lowdragmc.kilagraphdemo.client.ui.ServerHologramBrowseUI(GlobalPos.of(level.dimension(), pos), be);
        setScreen(ui.getRoot());
    }

    /** Open the editor on {@code display}; {@code onSaved} receives the graph tag, model + deps on save. */
    public static void openEditor(GlobalPos blockPos, HologramDisplay display, ModelSelection model,
                                  HologramEditorWindow.SaveCallback onSaved) {
        var handle = HologramEditorWindow.build(blockPos, display, model, onSaved);
        var modularUI = new ModularUI(UI.of(handle.root, StylesheetManager.ORE_MERGED));
        Minecraft.getInstance().setScreen(new HologramEditorScreen(modularUI, handle));
    }

    private static void setScreen(com.lowdragmc.lowdraglib2.gui.ui.UIElement root) {
        var modularUI = new ModularUI(UI.of(root, StylesheetManager.ORE_MERGED));
        Minecraft.getInstance().setScreen(new ModularUIScreen(modularUI, Component.empty()));
    }
}
