package com.lowdragmc.kilagraphdemo.client.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Client-only top-bar control letting the player change the MC GUI scale while a graph editor is open,
 * shared by the hologram editor and the drone station editor. Behaves like LDLib2's
 * {@code AppearanceSettings} screen-scale option, with two differences:
 *
 * <ul>
 *   <li>The change is <b>temporary</b>: the scale captured when the editor opened is restored when the
 *       UI closes (via the root's {@link UIEvents#REMOVED} event).</li>
 *   <li>The chosen scale is still remembered to a client-local file and <b>re-applied on next open</b>,
 *       but only if the current window still supports it (clamped to the max scale).</li>
 * </ul>
 *
 * <p>One shared preference backs both editors. The button shows the current scale ("Auto"/number) and
 * opens a popup menu of the supported scales, mirroring the graph view's right-click context menu.</p>
 */
public final class ScreenScaleControl {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Single shared preference (mirrors the {@code gameDirectory/kilagraphdemo/...} convention). */
    private static final File FILE = new File(Minecraft.getInstance().gameDirectory, "kilagraphdemo/screen_scale.json");
    private static final String KEY = "screenScale";

    /** The player's true GUI scale when the editor opened; restored on close. */
    private final int originalScale;
    @Nullable
    private Button button;

    private ScreenScaleControl() {
        this.originalScale = Minecraft.getInstance().options.guiScale().get();
    }

    /**
     * Capture the current scale, apply any saved preference (if the window supports it), and wire the
     * restore-on-close so the player's original scale comes back when {@code root} is removed.
     */
    public static ScreenScaleControl install(UIElement root) {
        ScreenScaleControl control = new ScreenScaleControl();
        int saved = loadSaved();
        if (saved >= 0) {
            control.applyScale(saved);
        }
        root.addEventListener(UIEvents.REMOVED, e -> control.applyScale(control.originalScale));
        return control;
    }

    /** Build the top-bar button; the caller sizes/places it to fit its bar. */
    public Button createButton() {
        Button b = new Button();
        b.style(s -> s.appendTooltipsString("kilagraphdemo.ui.common.screen_scale.tooltip"));
        b.setOnClick(this::openMenu);
        this.button = b;
        refreshButtonText();
        return b;
    }

    /** Update the button label to the current scale ("Auto" for 0, otherwise the number). */
    private void refreshButtonText() {
        if (button == null) return;
        int current = Minecraft.getInstance().options.guiScale().get();
        if (current == 0) {
            button.setText("options.guiScale.auto", true);
        } else {
            button.setText(String.valueOf(current), false);
        }
    }

    /** Popup menu of supported scales (Auto + 1..max), checking the current one. */
    private void openMenu(UIEvent e) {
        if (button == null) return;
        var mui = button.getModularUI();
        if (mui == null) return;
        Minecraft mc = Minecraft.getInstance();
        int current = mc.options.guiScale().get();
        int maxScale = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
        var menu = TreeBuilder.Menu.start();
        for (int i = 0; i <= maxScale; i++) {
            int sel = i;
            menu.leaf(current == i ? Icons.CHECK : IGuiTexture.EMPTY,
                    i == 0 ? "options.guiScale.auto" : String.valueOf(i),
                    () -> {
                        applyScale(sel);
                        saveSaved(sel);
                        refreshButtonText();
                    });
        }
        var off = mui.ui.rootElement.worldToLocalLayoutOffset(new Vector2f(e.x, e.y));
        var ctx = new Menu<>(menu.build(), TreeBuilder.Menu::uiProvider)
                .setHoverTextureProvider(TreeBuilder.Menu::hoverTextureProvider)
                .setOnNodeClicked(TreeBuilder.Menu::handle);
        Style.importantPipeline(ctx.getLayout(), l -> l.left(off.x).top(off.y));
        mui.ui.rootElement.addChild(ctx);
    }

    /** Apply a scale (clamped to the window's supported range), re-laying out the open screen. */
    private void applyScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        int maxScale = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
        int clamped = Math.max(0, Math.min(scale, maxScale));
        var guiScale = mc.options.guiScale();
        if (guiScale.get() != clamped) {
            guiScale.set(clamped);
            mc.resizeGui();
        }
    }

    /** The saved scale preference, or {@code -1} if none. */
    public static int loadSaved() {
        if (!FILE.exists()) return -1;
        try (var reader = new FileReader(FILE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.has(KEY) ? json.get(KEY).getAsInt() : -1;
        } catch (Exception e) {
            LOGGER.error("Failed to read screen scale preference {}", FILE, e);
            return -1;
        }
    }

    /** Persist the chosen scale as the shared preference. */
    public static void saveSaved(int scale) {
        if (FILE.getParentFile() != null) {
            FILE.getParentFile().mkdirs();
        }
        JsonObject json = new JsonObject();
        json.addProperty(KEY, scale);
        try (var writer = new FileWriter(FILE)) {
            writer.write(json.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to write screen scale preference {}", FILE, e);
        }
    }
}
