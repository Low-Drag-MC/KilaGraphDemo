package com.lowdragmc.kilagraphdemo.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Demo-side workaround that actually disables the inventory key ('e') closing a {@code BlockUIMenuType}
 * menu, which {@code ModularUI.shouldCloseOnKeyInventory(false)} alone does not for these menus.
 *
 * <p>Those menus render on an {@code AbstractContainerScreen}. LDLib2's {@code ScreenMixin} only suppresses
 * the inventory-key close when {@code widget.keyPressed()} returns {@code true}; otherwise the vanilla
 * {@code AbstractContainerScreen.keyPressed} falls through to {@code onClose()}. {@code widget.keyPressed()}
 * only reports the key handled when the UI has a focused element whose event path carries a KEY_DOWN
 * listener — and a freshly-opened menu (or one where the player clicked empty space) has no focus, so 'e'
 * closes it.</p>
 *
 * <p>This pins focus to the menu root whenever nothing else is focused (so the key is dispatched as a
 * KEY_DOWN at all) and swallows the inventory key with a capture-phase listener — the listener simply
 * running is what makes {@code widget.keyPressed()} report the event handled. Focus on child widgets (text
 * fields, graph nodes) is left untouched, so normal editing still works.</p>
 */
public final class InventoryKeyGuard {

    private InventoryKeyGuard() {
    }

    /** Install the guard on a menu UI's {@code root} element. Call once while building the UI. */
    public static void install(UIElement root) {
        root.setFocusable(true);
        // Swallow the inventory key in the capture phase. The listener running is what makes
        // widget.keyPressed() report "handled", which is what stops the container screen from closing.
        root.addEventListener(UIEvents.KEY_DOWN, e -> {
            // Mirror InputConstants.getKey(KeyEvent): keyCode -1 (unknown) means look up by scancode.
            var key = e.keyCode == InputConstants.UNKNOWN.getValue()
                    ? InputConstants.Type.SCANCODE.getOrCreate(e.scanCode)
                    : InputConstants.Type.KEYSYM.getOrCreate(e.keyCode);
            if (Minecraft.getInstance().options.keyInventory.isActiveAndMatches(key)) {
                e.stopImmediatePropagation();
            }
        }, true);
        // Keep something focused so the key is dispatched at all: when focus is lost (on open, or after
        // clicking empty / non-focusable space) re-grab it on the root. Child-widget focus is left alone.
        root.addEventListener(UIEvents.TICK, e -> {
            var mui = root.getModularUI();
            if (mui != null && mui.getFocusedElement() == null) {
                root.focus();
            }
        });
    }
}
