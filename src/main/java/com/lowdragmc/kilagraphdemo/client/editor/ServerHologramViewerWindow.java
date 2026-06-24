package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphEditorView;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.render.ServerHologramDisplays;
import com.lowdragmc.kilagraphdemo.client.ui.WorldViewPanel;
import com.lowdragmc.lowdraglib2.editor.ui.SplittableWindow;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.DockSlot;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;

/**
 * Read-only viewer for a server hologram's displayed work — a stripped-down sibling of
 * {@link HologramEditorWindow}. Two panes only: the displayed work's {@link RenderTypeGraph} on the
 * left (pan/zoom only, no editing) and the live {@link WorldViewPanel} on the right. No
 * Shader-Function view, no model view, no save. Opened by any player via a normal right-click; the
 * privileged "set display" browser stays behind shift + right-click (see
 * {@code ServerHologramBlock#useWithoutItem}).
 *
 * <p>Read-only is enforced exactly as {@code DroneRankingClientUI}: drop the save button, intercept
 * every graph-mutating command, and collapse all dock panels (settings/preview/blackboard/inspector)
 * so only the node canvas remains. Pan, zoom and selection are canvas-level (not commands) so they
 * keep working.</p>
 *
 * <p>Instance-based so it can poll for a not-yet-downloaded work: the renderer normally has the work
 * cached already (it resolves it every frame while the hologram is on-screen), so {@link #tryLoad}
 * usually succeeds on the first tick; otherwise {@link ServerHologramDisplays#resolve} kicks off the
 * chunked download and {@code tryLoad} keeps polling until it lands.</p>
 */
public final class ServerHologramViewerWindow {

    private static final DockSlot[] ALL_SLOTS = {
            DockSlot.TOP_LEFT, DockSlot.TOP_RIGHT, DockSlot.BOTTOM_LEFT, DockSlot.BOTTOM_RIGHT
    };

    // RenderTypeGraphView has a public no-arg ctor → use it directly; no export wiring needed here.
    private final RenderTypeGraphEditorView editorView =
            new RenderTypeGraphEditorView(RenderTypeGraphView::new);
    private final LocalShaderFunctions store = new LocalShaderFunctions();
    private final String uid;
    private boolean loaded;
    private int pendingFit = -1;

    private ServerHologramViewerWindow(String uid) {
        this.uid = uid;
    }

    /** Build the read-only viewer root for the work {@code uid} displayed by the server hologram. */
    public static UIElement build(String uid) {
        return new ServerHologramViewerWindow(uid).buildInto();
    }

    private UIElement buildInto() {
        SplittableWindow root = new SplittableWindow();
        var split = root.splitStyle(s -> s.percentage(70).minPercentage(20).maxPercentage(88))
                .splitNew(SplittableWindow.Edge.RIGHT);
        split.getFirst().getLeftTop().addView(editorView);
        split.getSecond().getLeftTop().addView(new WorldViewPanel());
        installReadOnlyGuard();
        root.addEventListener(UIEvents.TICK, e -> onTick());
        return root;
    }

    /**
     * Block all editing input at the editor layer so the graph is view-only — including the right-click
     * context menu, which {@code setCommandInterceptor} alone doesn't suppress (the menu opens on MOUSE_UP
     * and its add-node commands merely no-op). Capture-phase listeners on {@code editorView} run before the
     * graph's own handlers, so stopping propagation makes it read-only. Navigation stays live: middle/right
     * drag pans (MOUSE_DOWN button 1/2) and the wheel zooms — neither is blocked here. Mirrors
     * {@code DroneStationClientUI.installReadOnlyGuard}.
     */
    private void installReadOnlyGuard() {
        editorView.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) e.stopPropagation(); // left select / node drag / wire
        }, true);
        editorView.addEventListener(UIEvents.MOUSE_UP, e -> {
            if (e.button == 0 || e.button == 1) e.stopPropagation(); // click + right-click create menu
        }, true);
        for (String type : new String[]{UIEvents.KEY_DOWN, UIEvents.EXECUTE_COMMAND, UIEvents.DRAG_PERFORM}) {
            editorView.addEventListener(type, UIEvent::stopPropagation, true);
        }
    }

    private void onTick() {
        if (!loaded) tryLoad();
        if (pendingFit > 0 && --pendingFit == 0) editorView.graphView.fitGraphChildren();
    }

    /** Pull the display from the same cache the renderer uses; this also drives the lazy download. */
    private void tryLoad() {
        HologramDisplay display = ServerHologramDisplays.resolve(uid).display(); // uid=="" → null
        if (display == null) return;
        RenderTypeGraph graph = display.graph();
        graph.graphModel.setReferenceResolver(store::resolve); // resolve external shader-fn subgraphs
        editorView.loadGraph(graph, tag -> {}); // no-op save
        editorView.saveButton.removeSelf();
        editorView.graphView.setCommandInterceptor(cmd -> false); // block all mutating commands
        collapseAllPanels();
        loaded = true;
        pendingFit = 3; // re-fit once the new graph's elements have been laid out
    }

    /** Collapse every corner dock panel — this is a viewer, so only the node canvas should show. */
    private void collapseAllPanels() {
        for (DockSlot slot : ALL_SLOTS) {
            GraphPanel panel = editorView.graphView.dockManager.getCornerPanel(slot);
            if (panel != null && !panel.isCollapsed()) {
                panel.collapseToggle.setOn(true, true);
            }
        }
    }
}
