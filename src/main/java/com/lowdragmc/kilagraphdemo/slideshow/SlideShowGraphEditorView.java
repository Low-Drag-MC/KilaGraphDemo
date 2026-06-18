package com.lowdragmc.kilagraphdemo.slideshow;

import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import net.minecraft.nbt.CompoundTag;

/**
 * Editor view for a {@link SlideShowGraph}: a {@link GraphEditorView} whose canvas is a
 * {@link SlideShowGraphView} (settings panel hidden) and whose serialization routes through
 * {@link SlideShowGraphResource} (node model only — settings are locked/implicit).
 */
public class SlideShowGraphEditorView extends GraphEditorView {

    public SlideShowGraphEditorView() {
        super(SlideShowGraphView::new);
    }

    @Override
    public CompoundTag serializeGraph() {
        return getGraph() instanceof SlideShowGraph slideShowGraph
                ? SlideShowGraphResource.INSTANCE.serializeGraph(slideShowGraph)
                : super.serializeGraph();
    }
}
