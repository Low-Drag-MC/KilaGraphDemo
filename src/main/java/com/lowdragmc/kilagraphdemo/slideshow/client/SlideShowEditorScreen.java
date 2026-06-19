package com.lowdragmc.kilagraphdemo.slideshow.client;

import com.google.common.util.concurrent.Runnables;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Screen hosting the SlideShow graph editor. Like {@code HologramEditorScreen}, ESC is intercepted so unsaved
 * graph changes first raise a save/don't-save/cancel prompt (ESC is offered to the UI first so an open
 * dialog/dropdown consumes it).
 */
public class SlideShowEditorScreen extends ModularUIScreen {

    private final SlideShowEditorWindow.Handle handle;

    public SlideShowEditorScreen(ModularUI modularUI, SlideShowEditorWindow.Handle handle) {
        super(modularUI, Component.empty());
        this.handle = handle;
        handle.installExitButton(this::requestClose);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        return false;
    }

    private void requestClose() {
        if (handle.isDirty()) {
            Dialog.showCancelableCheck("Dialog.notify", "view.save_before_close.info", save -> {
                if (save) handle.save();
                closeNow();
            }, Runnables.doNothing()).show(handle.root.getModularUI());
        } else {
            closeNow();
        }
    }

    private void closeNow() {
        Minecraft.getInstance().popGuiLayer();
    }
}
