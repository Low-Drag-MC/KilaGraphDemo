package com.lowdragmc.kilagraphdemo.client.drone;

import com.lowdragmc.kilagraphdemo.block.DroneRankingBlockEntity;
import com.lowdragmc.kilagraphdemo.drone.DroneRankingMenuSync;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.DockSlot;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodeElement;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Client-only contents of the drone ranking block's spectator menu: a {@link GraphEditorView} showing the
 * selected leaderboard solution in <b>read-only</b> mode (no editing, no Upload/Run/Step controls, no
 * tick/score status) with the currently-executing node highlighted, plus a header line giving the author,
 * their official score and their rank. The graph can be panned and zoomed but not changed.
 *
 * <p>Built from {@link com.lowdragmc.kilagraphdemo.block.DroneRankingBlock#createUI}, only inside its
 * client-side guard, so a dedicated server never classloads this. Run state, the executing node UID, the
 * displayed program graph and the author/score/rank arrive over the menu's per-open-player sync
 * ({@link DroneRankingMenuSync}); this UI reads them from the sync bindings. The shown program can change
 * while the menu is open (the redstone selection changing), so the editor reloads whenever it does.</p>
 */
public final class DroneRankingClientUI {

    private final BlockPos blockPos;
    private final DroneRankingMenuSync.Bindings sync;

    private final GraphEditorView editorView = new GraphEditorView();
    /** Transparent right half: shows the world (the farm board) with a free-fly camera. */
    private final UIElement worldPanel = new UIElement();
    private final Label header = new Label();

    /** The program tag currently loaded into the editor; reloaded when the synced program differs. */
    private CompoundTag loadedProgram = new CompoundTag();
    private boolean loadedOnce;

    private String highlightUid = "";
    @Nullable
    private NodeElement highlightedNode;
    private int highlightFrame;
    private int pendingFit = -1;

    // free-fly camera input state
    private boolean looking;
    private float lastMouseX;
    private float lastMouseY;

    private static final double FLY_RADIUS = 32;
    private static final double FLY_SPEED = 0.45;
    private static final float LOOK_SENSITIVITY = 0.15f;
    private static final double ZOOM_STEP = 1.0;

    private DroneRankingClientUI(BlockPos blockPos, DroneRankingMenuSync.Bindings sync) {
        this.blockPos = blockPos;
        this.sync = sync;
    }

    /** Build the menu's {@link ModularUI} for the opening client player. */
    public static ModularUI build(BlockUIMenuType.BlockUIHolder holder, @Nullable DroneRankingBlockEntity be) {
        UIElement root = new UIElement();
        ModularUI ui = new ModularUI(UI.of(root, StylesheetManager.ORE_MERGED), holder.player);
        DroneRankingMenuSync.Bindings sync = DroneRankingMenuSync.register(ui, true, be);
        new DroneRankingClientUI(holder.pos, sync).buildInto(root);
        return ui;
    }

    private void buildInto(UIElement root) {
        // Root is a transparent full-screen row: the left half holds the read-only editor, the right half is
        // left empty so the world (the farm board) shows through behind the menu screen.
        root.getLayout().widthPercent(100).heightPercent(100);

        UIElement leftPanel = new UIElement().addClass("panel_bg");
        leftPanel.getLayout().widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(2);

        worldPanel.getLayout().widthPercent(100).heightPercent(100);
        SplitView.Horizontal split = new SplitView.Horizontal();
        split.getLayout().widthPercent(100).heightPercent(100);
        split.left(leftPanel).right(worldPanel).setPercentage(50);
        root.addChild(split);

        // Start with an empty graph; the selected solution loads once it arrives over the sync channel.
        loadIntoEditor(new DroneGraph());

        leftPanel.addChild(headerBar());

        editorView.getLayout().widthPercent(100).flex(1);
        leftPanel.addChild(editorView);
        // Keep the blackboard (TOP_LEFT) and inspector (TOP_RIGHT) panels collapsed: this is a viewer.
        collapsePanel(DockSlot.TOP_LEFT);
        collapsePanel(DockSlot.TOP_RIGHT);

        // Fly the render camera over the board while open; restore it on close. Flag this board so the
        // renderer overlays its coordinate labels.
        OpenDroneBoard.pos = blockPos;
        activateCamera();
        installFlyControls(root);
        root.addEventListener(UIEvents.REMOVED, e -> {
            CameraOverrideManager.INSTANCE.deactivate();
            if (blockPos.equals(OpenDroneBoard.pos)) OpenDroneBoard.pos = null;
        });

        root.addEventListener(UIEvents.TICK, e -> onTick());
        refreshHeader();
    }

    private UIElement headerBar() {
        UIElement bar = new UIElement();
        bar.getLayout().widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2).paddingAll(2);
        header.getTextStyle().textAlignVertical(Vertical.CENTER).textShadow(false);
        header.getLayout().flex(1);
        header.textStyle(s -> s.textColor(ColorPattern.BLUE.color));
        bar.addChild(header);
        return bar;
    }

    /**
     * Top-down orthographic view: camera straight above the block looking down, shifted horizontally so the
     * farm sits in the visible right half of the screen (the left half is the editor panel).
     */
    private void activateCamera() {
        Vec3 center = Vec3.atCenterOf(blockPos);
        Vec3 camPos = center.add(-5, 5, 5);
        CameraOverrideManager.INSTANCE.activate(camPos, 30f + 180, 55f, center, FLY_RADIUS);
    }

    /** Free-fly the camera from the right (world) panel: drag to look, scroll to dolly, WASD/space/shift to fly. */
    private void installFlyControls(UIElement root) {
        worldPanel.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            looking = true;
            lastMouseX = e.x;
            lastMouseY = e.y;
        });
        root.addEventListener(UIEvents.MOUSE_UP, e -> looking = false);
        root.addEventListener(UIEvents.MOUSE_MOVE, e -> {
            if (!looking) return;
            CameraOverrideManager.INSTANCE.rotate((e.x - lastMouseX) * LOOK_SENSITIVITY,
                    (e.y - lastMouseY) * LOOK_SENSITIVITY);
            lastMouseX = e.x;
            lastMouseY = e.y;
        });
        worldPanel.addEventListener(UIEvents.MOUSE_WHEEL, e -> CameraOverrideManager.INSTANCE.zoom(e.deltaY * ZOOM_STEP));
    }

    private void pollFlyKeys() {
        CameraOverrideManager mgr = CameraOverrideManager.INSTANCE;
        if (!mgr.isActive() || !(looking || worldPanel.isSelfOrChildHover())) return;
        var window = Minecraft.getInstance().getWindow();
        double forward = held(window, GLFW.GLFW_KEY_W) - held(window, GLFW.GLFW_KEY_S);
        double strafe = held(window, GLFW.GLFW_KEY_D) - held(window, GLFW.GLFW_KEY_A);
        double up = held(window, GLFW.GLFW_KEY_SPACE) - held(window, GLFW.GLFW_KEY_LEFT_SHIFT);
        mgr.fly(forward * FLY_SPEED, strafe * FLY_SPEED, up * FLY_SPEED);
    }

    private static int held(com.mojang.blaze3d.platform.Window window, int key) {
        return InputConstants.isKeyDown(window, key) ? 1 : 0;
    }

    /** Load a graph into the editor as a read-only view: drop the save button and reject all edit commands. */
    private void loadIntoEditor(DroneGraph graph) {
        editorView.loadGraph(graph, tag -> { });
        editorView.saveButton.removeSelf();
        // Read-only: block every graph-mutating command (create/delete/move/wire). Pan, zoom and selection
        // are canvas-level, not commands, so they keep working.
        editorView.graphView.setCommandInterceptor(cmd -> false);
        highlightedNode = null; // elements were rebuilt; drop the stale highlight reference
        highlightUid = "";
        pendingFit = 3; // re-fit once the new graph's elements have been laid out (see onTick)
    }

    private void onTick() {
        pollFlyKeys();
        reloadProgramIfChanged();
        tickPendingFit();
        RunState state = runState();
        boolean running = state == RunState.RUNNING || state == RunState.PAUSED;
        applyHighlight(running ? stringSync(sync.currentNode()) : "");
        animateHighlight();
        refreshHeader();
    }

    private void tickPendingFit() {
        if (pendingFit > 0 && --pendingFit == 0) {
            editorView.graphView.fitGraphChildren();
        }
    }

    private void collapsePanel(DockSlot slot) {
        GraphPanel panel = editorView.graphView.dockManager.getCornerPanel(slot);
        if (panel != null && !panel.isCollapsed()) {
            panel.collapseToggle.setOn(true, true);
        }
    }

    /** Reload the editor whenever the synced program changes (the redstone-selected rank switching). */
    private void reloadProgramIfChanged() {
        Tag tag = sync.program().getSyncValue().getValue();
        if (!(tag instanceof CompoundTag program)) return;
        if (loadedOnce && program.equals(loadedProgram)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        loadedProgram = program.copy();
        loadedOnce = true;
        DroneGraph graph = program.isEmpty()
                ? new DroneGraph()
                : DroneGraphCodec.fromTag(program, mc.level.registryAccess());
        loadIntoEditor(graph);
    }

    /** Default node selection border (restored when a node stops being the running highlight). */
    private static final IGuiTexture DEFAULT_FOCUS = ColorPattern.BLUE.borderTexture(1);

    /** Select the node currently executing on the server (empty UID clears the selection). */
    private void applyHighlight(String uid) {
        if (uid.equals(highlightUid)) return;
        if (highlightedNode != null) {
            highlightedNode.getNodeStyle().focusOverlay(DEFAULT_FOCUS);
            highlightedNode = null;
        }
        highlightUid = uid;
        editorView.graphView.clearAllSelected();
        if (uid.isEmpty()) return;
        Graph graph = editorView.getGraph();
        if (graph == null) return;
        try {
            var model = graph.graphModel.getModel(UUID.fromString(uid));
            if (model != null) {
                editorView.graphView.addSelected(model);
                if (editorView.graphView.getModelElement(model) instanceof NodeElement ne) {
                    highlightedNode = ne;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // malformed UID — nothing to highlight
        }
    }

    /** Pulse the running node's border through an animated rainbow, a touch wider than normal selection. */
    private void animateHighlight() {
        if (highlightedNode == null) return;
        highlightedNode.getNodeStyle().focusOverlay(
                ColorPattern.WHITE.borderTexture(2).copy().setColor(rainbow(highlightFrame++)));
    }

    /** Opaque ARGB rainbow colour cycling with {@code frame} (three phase-shifted sine channels). */
    private static int rainbow(int frame) {
        float t = frame * 0.08f;
        int r = (int) ((Mth.sin(t) * 0.5f + 0.5f) * 255);
        int g = (int) ((Mth.sin(t + 2.0944f) * 0.5f + 0.5f) * 255);
        int b = (int) ((Mth.sin(t + 4.18879f) * 0.5f + 0.5f) * 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Header: {@code "#<rank>  <author>  ·  score <officialScore>"}, or a placeholder when no entry. */
    private void refreshHeader() {
        int rank = intSync(sync.rank());
        String author = stringSync(sync.authorName());
        int score = intSync(sync.score());
        if (author.isEmpty()) {
            header.setText(Component.translatable("kilagraphdemo.ui.drone_ranking.no_entry", rank));
        } else {
            header.setText(Component.translatable("kilagraphdemo.ui.drone_ranking.header", rank, author, score));
        }
    }

    private RunState runState() {
        int ord = intSync(sync.runState());
        RunState[] values = RunState.values();
        return ord >= 0 && ord < values.length ? values[ord] : RunState.IDLE;
    }

    private static int intSync(SimpleBinding<Integer> binding) {
        Integer v = binding.getSyncValue().getValue();
        return v == null ? 0 : v;
    }

    private static String stringSync(SimpleBinding<String> binding) {
        String v = binding.getSyncValue().getValue();
        return v == null ? "" : v;
    }
}
