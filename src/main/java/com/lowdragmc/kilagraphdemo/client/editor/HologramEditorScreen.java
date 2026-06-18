package com.lowdragmc.kilagraphdemo.client.editor;

import com.google.common.util.concurrent.Runnables;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Screen hosting the hologram editor. Unlike a plain {@link ModularUIScreen}, ESC does not close immediately:
 * it's intercepted ({@link #shouldCloseOnEsc()} is disabled) so unsaved changes — graph <i>or</i> model — first
 * raise a save/don't-save/cancel prompt. ESC is still offered to the UI first (so an open dialog/dropdown
 * consumes it) before the close request is considered.
 */
public class HologramEditorScreen extends ModularUIScreen {

    private final HologramEditorWindow.Handle handle;

    public HologramEditorScreen(ModularUI modularUI, HologramEditorWindow.Handle handle) {
        super(modularUI, Component.empty());
        this.handle = handle;
        // A header "exit" button that closes the same way Esc does (prompt-on-unsaved).
        handle.installExitButton(this::requestClose);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // handled explicitly in keyPressed so we can prompt on unsaved changes
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true; // let the UI consume ESC first
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
        Minecraft.getInstance().setScreen(null);
    }
}
