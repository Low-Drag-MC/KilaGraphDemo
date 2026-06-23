package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import dev.vfyjxf.taffy.style.FlexDirection;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A reusable "Placement…" dialog for editing a hologram's per-block {@link HologramPlacement} — a
 * {@link TransformPanel} (offset/scale/rotation) plus a spin-speed field. Two modes:
 * <ul>
 *   <li>live ({@code onApply == null}): every edit calls {@code onChange} immediately (regular hologram —
 *       runtime, no network);</li>
 *   <li>apply ({@code onApply != null}): edits update a working copy and {@code onChange}, and an extra
 *       <b>Apply</b> button commits via {@code onApply} (server hologram — one packet, no per-keystroke spam).</li>
 * </ul>
 */
public final class PlacementDialog {

    private PlacementDialog() {
    }

    public static void open(UIElement anchor, HologramPlacement initial,
                            Consumer<HologramPlacement> onChange, @Nullable Consumer<HologramPlacement> onApply) {
        HologramPlacement[] cur = {initial};
        Dialog dialog = new Dialog();
        dialog.setTitle("kilagraphdemo.ui.editor.placement_title");

        UIElement content = new UIElement();
        content.getLayout().flexDirection(FlexDirection.COLUMN).width(180).gapAll(2);
        content.addChild(TransformPanel.build(
                () -> cur[0].transform(),
                t -> { cur[0] = cur[0].withTransform(t); onChange.accept(cur[0]); }));

        NumberConfigurator spin = new NumberConfigurator("kilagraphdemo.ui.editor.spin",
                () -> cur[0].spinDegreesPerTick(),
                n -> { cur[0] = cur[0].withSpin(n.floatValue()); onChange.accept(cur[0]); },
                initial.spinDegreesPerTick(), false);
        spin.setType(ConfigNumber.Type.FLOAT);
        spin.setRange(-45f, 45f);
        spin.setWheel(0.5f);
        content.addChild(spin);

        dialog.addContent(content);
        // Reset to defaults. The configurators read their value once, so reopen the dialog (rebuilt from
        // DEFAULT) rather than refreshing each field in place; the live onChange is fired too.
        dialog.addButton(new Button().setText("kilagraphdemo.ui.editor.reset").setOnClick(e -> {
            onChange.accept(HologramPlacement.DEFAULT);
            dialog.close();
            open(anchor, HologramPlacement.DEFAULT, onChange, onApply);
        }).style(s -> s.appendTooltipsString("kilagraphdemo.ui.editor.reset.tooltip")));
        if (onApply != null) {
            dialog.addButton(new Button().setText("kilagraphdemo.ui.editor.apply").setOnClick(e -> {
                onApply.accept(cur[0]);
                dialog.close();
            }).style(s -> s.appendTooltipsString("kilagraphdemo.ui.editor.apply.tooltip")));
        }
        dialog.setClickOutsideClose(true);
        dialog.overlay.layout(LayoutStyle::widthAuto);
        dialog.contentContainer.layout(LayoutStyle::widthAuto);
        dialog.show(anchor.getModularUI());
    }
}
