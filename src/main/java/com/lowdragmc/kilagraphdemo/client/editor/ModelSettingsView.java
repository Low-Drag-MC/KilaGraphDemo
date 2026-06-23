package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.client.HologramProjection;
import com.lowdragmc.kilagraphdemo.client.model.ObjContents;
import com.lowdragmc.kilagraphdemo.client.model.SubdividedContents;
import com.lowdragmc.kilagraphdemo.client.render.HologramAabbOverlay;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.render.HologramPlacements;
import com.lowdragmc.kilagraphdemo.block.HologramBlock;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.lowdragmc.kilagraphdemo.graph.ModelTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.phys.AABB;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Editor panel for a work's display geometry. A {@link Selector} chooses the model kind — a built-in primitive
 * (cube/sphere/quad) or a custom OBJ. Built-ins expose a subdivision count; a custom OBJ exposes a file picker,
 * a UV V-flip, and an offset/scale/rotation {@link ModelTransform}. Changes update the live
 * {@link HologramDisplay} immediately and are reported via {@code onChanged} (which also marks the editor dirty
 * so the work's Save covers model edits, not just graph edits).
 */
public class ModelSettingsView extends View {

    private static final String LBL_CUBE = "kilagraphdemo.ui.editor.kind_cube";
    private static final String LBL_SPHERE = "kilagraphdemo.ui.editor.kind_sphere";
    private static final String LBL_QUAD = "kilagraphdemo.ui.editor.kind_quad";
    private static final String LBL_CUSTOM = "kilagraphdemo.ui.editor.kind_custom";

    private final GlobalPos blockPos;
    private final HologramDisplay display;
    private final Consumer<ModelSelection> onChanged;
    private final Label info = new Label();
    private final UIElement body = new UIElement();

    private ModelSelection model;

    public ModelSettingsView(GlobalPos blockPos, HologramDisplay display, ModelSelection initial,
                             Consumer<ModelSelection> onChanged) {
        this.blockPos = blockPos;
        this.display = display;
        this.onChanged = onChanged;
        this.model = initial;
        setName("kilagraphdemo.ui.editor.view_model");
        getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).heightPercent(100).gapAll(2).paddingAll(3);

        info.textStyle(style -> style.adaptiveHeight(true));
        addChild(info);

        Selector<String> selector = new Selector<>();
        selector.setCandidates(List.of(LBL_CUBE, LBL_SPHERE, LBL_QUAD, LBL_CUSTOM));
        selector.setValue(labelOf(model), false);
        selector.setOnValueChanged(this::onKindSelected);
        addChild(selector);

        // Render radius applies to every model kind (drives the renderer's cull box), so it lives outside the
        // kind-specific body and is always visible. A "Display AABB" button next to it previews the cull box.
        NumberConfigurator radius = new NumberConfigurator("kilagraphdemo.ui.editor.radius",
                () -> model.renderRadius(),
                n -> { model = model.withRenderRadius(n.floatValue()); apply(); },
                model.renderRadius(), false);
        radius.setType(ConfigNumber.Type.FLOAT);
        radius.setRange(0f, 256f);
        radius.setWheel(0.5f);
        radius.getLayout().flex(1);

        Button aabb = new Button().setText("kilagraphdemo.ui.editor.aabb");
        aabb.getLayout().width(36).height(14);
        aabb.style(s -> s.appendTooltipsString(
                "kilagraphdemo.ui.editor.aabb.tooltip.0",
                "kilagraphdemo.ui.editor.aabb.tooltip.1"));
        aabb.setOnClick(e -> showAabb());

        UIElement radiusRow = new UIElement();
        radiusRow.getLayout().flexDirection(FlexDirection.ROW).widthPercent(100).gapAll(2);
        radiusRow.addChildren(radius, aabb);
        addChild(radiusRow);

        // The kind-specific controls can overflow (OBJ has several rows), so host them in a scroller.
        body.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(2);
        ScrollerView scroller = new ScrollerView();
        scroller.getLayout().flex(1).widthPercent(100);
        scroller.addScrollViewChild(body);
        addChild(scroller);

        rebuildBody();
        refreshInfo();
    }

    // ---- kind selection ----------------------------------------------------------------------

    private void onKindSelected(String label) {
        String key = keyOf(label);
        if (ObjContents.OBJ.equals(key)) {
            if (!model.isObj()) model = ModelSelection.obj("");
        } else if (model.isObj() || !model.key().equals(key)) {
            model = ModelSelection.builtin(key, defaultSubdivisions(key));
        }
        rebuildBody();
        apply();
    }

    private static int defaultSubdivisions(String key) {
        return SubdividedContents.SPHERE.equals(key) ? 4 : 1; // a sphere needs more rings to look round
    }

    // ---- conditional body --------------------------------------------------------------------

    private void rebuildBody() {
        body.clearAllChildren();
        if (model.isObj()) {
            buildObjBody();
        } else {
            buildPrimitiveBody();
        }
    }

    private void buildPrimitiveBody() {
        NumberConfigurator subdiv = new NumberConfigurator("kilagraphdemo.ui.editor.subdivisions",
                () -> model.subdivisions(),
                n -> { model = model.withSubdivisions(n.intValue()); apply(); },
                model.subdivisions(), false);
        subdiv.setType(ConfigNumber.Type.INTEGER);
        subdiv.setRange(1, 64);
        subdiv.setWheel(1);
        body.addChild(subdiv);
    }

    private void buildObjBody() {
        body.addChild(new Button().setText("kilagraphdemo.ui.editor.import_obj").setOnClick(e -> importObj())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.editor.import_obj.tooltip")));

        body.addChild(new BooleanConfigurator("kilagraphdemo.ui.editor.flip_uv",
                () -> model.flipV(),
                b -> { model = model.withFlipV(b); apply(); },
                false, true));

        // Offset / Scale / Rotation as three compact xyz rows (baked into the OBJ mesh).
        body.addChild(TransformPanel.build(() -> model.transform(), this::setTransform));
    }

    private void setTransform(ModelTransform transform) {
        model = model.withTransform(transform);
        apply();
    }

    /** Show the renderer's cull box for the current radius + this block's placement as a 10s world wireframe. */
    private void showAabb() {
        Direction facing = Direction.UP;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var bs = level.getBlockState(blockPos.pos());
            if (bs.hasProperty(HologramBlock.FACING)) facing = bs.getValue(HologramBlock.FACING);
        }
        AABB box = HologramProjection.cullBox(blockPos.pos(), facing, HologramPlacements.resolve(blockPos),
                model.renderRadius());
        HologramAabbOverlay.show(blockPos, box);
    }

    /** Pick a {@code .obj} under {@code <assetsDir>/models} and use it as the display geometry. */
    private void importObj() {
        File assets = Kilagraphdemo.getAssetsDir();
        File modelsDir = new File(assets, "models");
        Dialog.showFileDialog("kilagraphdemo.ui.editor.file_obj_title", modelsDir, true, Dialog.suffixFilter(".obj"), file -> {
            if (file == null) return;
            String rel = assets.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            String location = Kilagraphdemo.MODID + ":" + rel;
            if (Identifier.tryParse(location) == null) {
                Dialog.showNotification("kilagraphdemo.ui.editor.dlg.invalid_model.title",
                        I18n.get("kilagraphdemo.ui.editor.dlg.invalid_model.body", rel), null).show(getModularUI());
                return;
            }
            model = model.isObj() ? model.withLocation(location) : ModelSelection.obj(location);
            apply();
        }).show(getModularUI());
    }

    // ---- apply / labels ----------------------------------------------------------------------

    private void apply() {
        display.setContent(model.toContent());
        onChanged.accept(model);
        refreshInfo();
    }

    private void refreshInfo() {
        info.setText(Component.translatable("kilagraphdemo.ui.editor.model_info", model.describe()));
    }

    private static String labelOf(ModelSelection model) {
        if (model.isObj()) return LBL_CUSTOM;
        return switch (model.key()) {
            case SubdividedContents.SPHERE -> LBL_SPHERE;
            case SubdividedContents.QUAD -> LBL_QUAD;
            default -> LBL_CUBE;
        };
    }

    private static String keyOf(String label) {
        return switch (label) {
            case LBL_SPHERE -> SubdividedContents.SPHERE;
            case LBL_QUAD -> SubdividedContents.QUAD;
            case LBL_CUSTOM -> ObjContents.OBJ;
            default -> SubdividedContents.CUBE;
        };
    }
}
