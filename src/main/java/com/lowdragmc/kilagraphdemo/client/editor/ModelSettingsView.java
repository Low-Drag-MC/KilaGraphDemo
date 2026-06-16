package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraphdemo.client.model.SubdividedContents;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Editor panel for a work's display geometry — primitive (quad/cube/sphere) and subdivision count.
 * Changes update the live {@link HologramDisplay} immediately (so the hologram reflects them) and are
 * reported via {@code onChanged} so they get persisted when the graph is saved.
 */
public class ModelSettingsView extends View {

    private final HologramDisplay display;
    private final Consumer<ModelSelection> onChanged;
    private final Label info = new Label();

    private ModelSelection model;

    public ModelSettingsView(HologramDisplay display, ModelSelection initial, Consumer<ModelSelection> onChanged) {
        this.display = display;
        this.onChanged = onChanged;
        this.model = initial;
        setName("Model");
        getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).heightPercent(100).gapAll(2).paddingAll(3);

        info.textStyle(style -> style.adaptiveHeight(true));
        addChild(info);
        addChild(new Button().setText("Model: cycle").setOnClick(e -> cycleModel()));
        addChild(new Button().setText("Subdiv +").setOnClick(e -> changeSubdiv(1)));
        addChild(new Button().setText("Subdiv -").setOnClick(e -> changeSubdiv(-1)));
        refresh();
    }

    private void cycleModel() {
        String next = switch (model.key()) {
            case SubdividedContents.QUAD -> SubdividedContents.CUBE;
            case SubdividedContents.CUBE -> SubdividedContents.SPHERE;
            default -> SubdividedContents.QUAD;
        };
        model = new ModelSelection(next, model.subdivisions());
        apply();
    }

    private void changeSubdiv(int delta) {
        model = new ModelSelection(model.key(), Math.max(1, model.subdivisions() + delta));
        apply();
    }

    private void apply() {
        display.setContent(model.toContent());
        onChanged.accept(model);
        refresh();
    }

    private void refresh() {
        info.setText(Component.literal("Model: " + model.key() + "  x" + model.subdivisions()));
    }
}
