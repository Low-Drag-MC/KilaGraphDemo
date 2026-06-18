package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.Sampler2DValue;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerAddress;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerFilter;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerMode;
import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.accessors.EnumAccessor;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * Editor configurator for a {@link com.lowdragmc.kilagraphdemo.graph.node.FileTextureNode}'s SAMPLER2D
 * option. Unlike KilaGraph's {@code Sampler2DConfigurator} it has no mode selector and no free-form location
 * field — the texture is chosen with a file picker over {@code <assetsDir>/textures} (PNG only) and stored as
 * a {@code kilagraphdemo:textures/…} location (always CUSTOM mode). Keeps the GPU sampler params
 * (filter / address / mipmap) and a live preview. Client-only; loaded lazily by the node.
 */
public final class FileTextureConfigurator {
    private FileTextureConfigurator() {}

    public static IConfigurable build(IFieldValueConfigurable vc) {
        Supplier<Sampler2DValue> get = () -> {
            Object v = vc.getValue();
            return v instanceof Sampler2DValue s ? s : Sampler2DValue.defaultValue();
        };

        // File picker button → fills in a kilagraphdemo:textures/… location (CUSTOM mode).
        var fileRow = new Configurator("kg.filetexture.file");
        var button = new Button().setText("Choose PNG…");
        button.getLayout().flexGrow(1).height(14);
        button.setOnClick(e -> openPicker(vc, get, button));
        fileRow.inlineContainer.addChild(button);

        var filter = EnumAccessor.create(
                "kg.filetexture.filter", List.of(SamplerFilter.values()),
                () -> get.get().filter(),
                f -> {
                    var c = get.get();
                    vc.setValue(new Sampler2DValue(c.location(), SamplerMode.CUSTOM, f, c.address(), c.mipmap()));
                },
                SamplerFilter.NEAREST, true);

        var address = EnumAccessor.create(
                "kg.filetexture.address", List.of(SamplerAddress.values()),
                () -> get.get().address(),
                a -> {
                    var c = get.get();
                    vc.setValue(new Sampler2DValue(c.location(), SamplerMode.CUSTOM, c.filter(), a, c.mipmap()));
                },
                SamplerAddress.CLAMP, true);

        var mipmap = new BooleanConfigurator(
                "kg.filetexture.mipmap",
                () -> get.get().mipmap(),
                mm -> {
                    var c = get.get();
                    vc.setValue(new Sampler2DValue(c.location(), SamplerMode.CUSTOM, c.filter(), c.address(), mm));
                },
                false, true);

        // Live preview of the chosen texture.
        var preview = new Configurator("kg.filetexture.preview");
        preview.inlineContainer.addChild(new UIElement()
                .layout(l -> l.width(40).height(40))
                .style(s -> s.backgroundTexture(DynamicTexture.of(() -> {
                    Identifier id = Identifier.tryParse(get.get().location());
                    return id == null ? IGuiTexture.EMPTY : SpriteTexture.of(id);
                }))));

        return IConfigurable.create(group -> group.addConfigurators(fileRow, filter, address, mipmap, preview));
    }

    /**
     * Open a PNG file picker over {@code <assetsDir>/textures} and store the choice as
     * {@code kilagraphdemo:textures/<relative-path>} — the id the file is loadable under at runtime.
     */
    private static void openPicker(IFieldValueConfigurable vc, Supplier<Sampler2DValue> get, UIElement anchor) {
        File assets = Kilagraphdemo.getAssetsDir();
        File texturesDir = new File(assets, "textures");
        Dialog.showFileDialog("Choose PNG", texturesDir, true, Dialog.suffixFilter(".png"), file -> {
            if (file == null) return;
            String rel = assets.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            String location = Kilagraphdemo.MODID + ":" + rel;
            if (Identifier.tryParse(location) == null) {
                Dialog.showNotification("Invalid texture name",
                        "Use lowercase a-z 0-9 / . _ - in the file path: " + rel, null).show(anchor);
                return;
            }
            Sampler2DValue c = get.get();
            vc.setValue(new Sampler2DValue(location, SamplerMode.CUSTOM, c.filter(), c.address(), c.mipmap()));
        }).show(anchor.getModularUI());
    }
}
