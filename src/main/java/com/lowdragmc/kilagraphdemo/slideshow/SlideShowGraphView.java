package com.lowdragmc.kilagraphdemo.slideshow;

import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import org.jetbrains.annotations.Nullable;

/**
 * Editor canvas for a {@link SlideShowGraph}. Identical to the RenderType view except the render-settings
 * panel is hidden — a SlideShowGraph's settings are locked to match the slide pipeline, so editing them is
 * meaningless. The live shader preview stays.
 */
public class SlideShowGraphView extends RenderTypeGraphView {

    @Override
    protected boolean shouldShowSettingsPanel(@Nullable Graph graph) {
        return !(graph instanceof SlideShowGraph);
    }
}
