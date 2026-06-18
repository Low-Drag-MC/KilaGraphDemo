package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraphdemo.graph.ModelTransform;
import com.lowdragmc.lowdraglib2.configurator.accessors.Vector3fAccessor;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.FlexDirection;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Compact editor for a {@link ModelTransform}: three xyz rows (Offset / Scale / Rotation) built with
 * {@link Vector3fAccessor} (so each is one line of three number fields, not nine stacked rows). Shared by the
 * OBJ model panel ({@link ModelSettingsView}) and the per-block hologram placement dialogs.
 */
public final class TransformPanel {

    private TransformPanel() {
    }

    /** Build a column of Offset/Scale/Rotation rows bound to {@code get}/{@code set}. */
    public static UIElement build(Supplier<ModelTransform> get, Consumer<ModelTransform> set) {
        UIElement column = new UIElement();
        column.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(2);
        Vector3fAccessor accessor = new Vector3fAccessor();
        column.addChild(accessor.create("Offset",
                () -> get.get().offsetVec(),
                v -> set.accept(get.get().withOffsetVec(v)), false, null, null));
        column.addChild(accessor.create("Scale",
                () -> get.get().scaleVec(),
                v -> set.accept(get.get().withScaleVec(v)), false, null, null));
        column.addChild(accessor.create("Rotation",
                () -> get.get().rotVec(),
                v -> set.accept(get.get().withRotVec(v)), false, null, null));
        return column;
    }
}
