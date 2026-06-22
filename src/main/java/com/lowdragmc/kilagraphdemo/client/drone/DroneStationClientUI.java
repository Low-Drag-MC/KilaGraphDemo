package com.lowdragmc.kilagraphdemo.client.drone;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.drone.DroneField;
import com.lowdragmc.kilagraphdemo.drone.DroneMenuSync;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.drone.node.ClearNode;
import com.lowdragmc.kilagraphdemo.drone.node.DronePrintNode;
import com.lowdragmc.kilagraphdemo.drone.node.HarvestNode;
import com.lowdragmc.kilagraphdemo.drone.node.MoveToCoordNode;
import com.lowdragmc.kilagraphdemo.drone.node.PlantNode;
import com.lowdragmc.kilagraphdemo.drone.node.ScanCellNode;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.kilagraphdemo.network.C2SDroneControl;
import com.lowdragmc.kilagraphdemo.network.C2SSaveDroneProgram;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.DockSlot;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.UUID;

/**
 * Client-only contents of the drone station's menu UI: a {@link GraphEditorView} editing the station's
 * {@link DroneGraph}, a control bar (Upload / Run / Pause / Resume / Step / Stop, owner-only) and a live
 * status line (state · tick · score).
 *
 * <p>Built from {@link com.lowdragmc.kilagraphdemo.block.DroneStationBlock#createUI}, only inside its
 * client-side guard, so a dedicated server never classloads this (or {@link GraphEditorView}). Run state,
 * the executing node UID, and the stored program graph arrive over the menu's per-open-player sync
 * ({@link DroneMenuSync}); this UI reads them from the sync bindings rather than the broadcast snapshot.</p>
 *
 * <p>The editor's built-in save button is removed: saving here means <em>uploading</em> to the server
 * block entity ({@link C2SSaveDroneProgram}). While a run is RUNNING/PAUSED the editor is read-only (its
 * graph view is deactivated, which also pauses pan/zoom) and the currently-executing node is highlighted
 * from the synced UID — the client graph is the same one that was uploaded, so node UIDs map reliably.</p>
 */
public final class DroneStationClientUI {

    /**
     * The station whose menu is open on this client, or {@code null}. Read by {@link DroneStationRenderer}
     * to draw the field's coordinate labels only for the station the player is currently programming.
     */
    @Nullable
    public static volatile BlockPos openStation;

    private final BlockPos stationPos;
    private final boolean owner;
    private final DroneMenuSync.Bindings sync;

    private final GraphEditorView editorView = new GraphEditorView();
    /** Transparent right half: shows the world; R4.3 wires free-fly camera control onto it. */
    private final UIElement worldPanel = new UIElement();
    private final Label status = new Label();
    /** Dock panel showing the run's print output, fed from the synced log each tick. */
    private final DroneLogPanel logPanel = new DroneLogPanel();
    private boolean programLoaded;

    /** Serialized graph as last sent to the server (upload/run/step) or loaded; used to detect ESC-close edits. */
    private CompoundTag lastSyncedTag = new CompoundTag();
    /** True while our unsaved-changes dialog is open, so the ESC handler lets the dialog consume its own ESC. */
    private boolean dialogOpen;

    // true while a run is in progress: capture-phase guards swallow editing input on the editor
    private boolean readOnly;
    private String highlightUid = "";

    // free-fly camera input state
    private boolean looking;
    private float lastMouseX;
    private float lastMouseY;

    private static final double FLY_RADIUS = 32;
    private static final double FLY_SPEED = 0.45;
    private static final float LOOK_SENSITIVITY = 0.15f;
    private static final double ZOOM_STEP = 1.0;

    private DroneStationClientUI(BlockPos stationPos, boolean owner, DroneMenuSync.Bindings sync) {
        this.stationPos = stationPos;
        this.owner = owner;
        this.sync = sync;
    }

    /** Build the menu's {@link ModularUI} for the opening client player. */
    public static ModularUI build(BlockUIMenuType.BlockUIHolder holder, @Nullable DroneStationBlockEntity be) {
        boolean owner = be != null && Minecraft.getInstance().player != null
                && be.isOwner(Minecraft.getInstance().player.getUUID());

        UIElement root = new UIElement();
        ModularUI ui = new ModularUI(UI.of(root, StylesheetManager.ORE_MERGED), holder.player);
        // We handle ESC ourselves so we can confirm before discarding unsaved graph edits.
        ui.shouldCloseOnEsc(false);
        DroneMenuSync.Bindings sync = DroneMenuSync.register(ui, true, be);

        new DroneStationClientUI(holder.pos, owner, sync).buildInto(root);
        return ui;
    }

    private void buildInto(UIElement root) {
        // Root is a transparent full-screen row: the left half holds the editor panel, the right half is
        // left empty so the world (the farm board) shows through behind the menu screen — the menu's
        // ModularUIContainerScreen draws no background of its own.
        root.getLayout().widthPercent(100).heightPercent(100);

        UIElement leftPanel = new UIElement().addClass("panel_bg");
        leftPanel.getLayout().widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(2);

        // Right half stays transparent so the world shows through; left holds the editor. The split bar
        // between them is draggable so the player can resize the editor vs. the farm view.
        worldPanel.getLayout().widthPercent(100).heightPercent(100);
        SplitView.Horizontal split = new SplitView.Horizontal();
        split.getLayout().widthPercent(100).heightPercent(100);
        split.left(leftPanel).right(worldPanel).setPercentage(50);
        root.addChild(split);

        // Open with a placeholder; the stored program loads once it arrives over the sync channel.
        loadIntoEditor(newProgram());

        leftPanel.addChild(controlBar());

        editorView.getLayout().widthPercent(100).flex(1);
        leftPanel.addChild(editorView);
        installReadOnlyGuard();
        installLogPanel();

        // Fly the render camera over the station while the UI is open; restore it when the screen closes
        // (REMOVED fires via ModularUI.onRemoved from Screen.removed()). Also flag this station so the
        // renderer overlays its coordinate labels, and clear both on close.
        openStation = stationPos;
        activateCamera();
        installFlyControls(root);
        root.addEventListener(UIEvents.REMOVED, e -> {
            CameraOverrideManager.INSTANCE.deactivate();
            if (stationPos.equals(openStation)) openStation = null;
        });

        // Intercept ESC (vanilla close is disabled via shouldCloseOnEsc(false)) so we can confirm before
        // throwing away un-uploaded graph edits. Capture phase so we see it before child elements.
        root.addEventListener(UIEvents.KEY_DOWN, e -> {
            if (e.keyCode != GLFW.GLFW_KEY_ESCAPE || dialogOpen) return; // let an open dialog handle its own ESC
            e.stopPropagation();
            if (isGraphDirty()) {
                promptCloseUnsaved();
            } else {
                closeScreen();
            }
        }, true);

        root.addEventListener(UIEvents.TICK, e -> onTick());
        refreshStatus(RunState.IDLE);
    }

    /** Ask whether to upload before closing when the graph has un-uploaded edits. */
    private void promptCloseUnsaved() {
        dialogOpen = true;
        Dialog.showCancelableCheck("Dialog.notify", "view.save_before_close.info", save -> {
            dialogOpen = false;
            if (save) {
                upload();
            }
            closeScreen();
        }, () -> dialogOpen = false).show(editorView.getModularUI());
    }

    /** Close the station menu (proper container close so the server releases the menu). */
    private void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.closeContainer();
        } else {
            mc.setScreen(null);
        }
    }

    /**
     * Top-down orthographic view: camera straight above the station looking down, shifted horizontally so
     * the farm sits in the visible right half of the screen (the left half is the editor panel).
     */
    private void activateCamera() {
        Vec3 center = Vec3.atCenterOf(stationPos);
        Vec3 camPos = center.add(-5, 5, 5); // straight above; X-shift frames it into the right half
        CameraOverrideManager.INSTANCE.activate(camPos, 30f + 180, 55f, center, FLY_RADIUS); // pitch 90 = straight down
    }

    /**
     * Free-fly the camera from the right (world) panel: drag to look, scroll to dolly, and WASD/space/shift
     * to fly (polled each tick while the panel is hovered or a look-drag is in progress, so typing in the
     * left editor is unaffected). All movement is tethered near the station by {@link CameraOverrideManager}.
     */
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

    /** Poll held movement keys and fly the camera, but only while the world panel is engaged. */
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

    /**
     * Swallow editing input on the editor while a run is in progress. Capture-phase listeners on the
     * editor run (root&rarr;target) before the node elements handle the event, so stopping propagation
     * makes the graph read-only (this also blocks pan/zoom, an accepted tradeoff). The control bar is a
     * sibling of the editor, so its buttons are unaffected.
     */
    private void installReadOnlyGuard() {
        // Block editing (left-button drag/select/wire, right-click create menu, keyboard delete/commands,
        // library drops) — but keep navigation: mouse wheel (zoom) and middle/right-button pan stay live.
        editorView.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (readOnly && e.button == 0) e.stopPropagation();
        }, true);
        editorView.addEventListener(UIEvents.MOUSE_UP, e -> {
            if (readOnly && (e.button == 0 || e.button == 1)) e.stopPropagation();
        }, true);
        for (String type : new String[]{UIEvents.KEY_DOWN, UIEvents.EXECUTE_COMMAND, UIEvents.DRAG_PERFORM}) {
            editorView.addEventListener(type, e -> {
                if (readOnly) e.stopPropagation();
            }, true);
        }
    }

    private UIElement controlBar() {
        UIElement bar = new UIElement();
        bar.getLayout().widthPercent(100).height(14).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2);

        if (owner) {
            bar.addChild(button("Upload", this::upload));
            bar.addChild(button("Run", this::run));
            bar.addChild(button("Pause", () -> control(C2SDroneControl.Action.PAUSE)));
            bar.addChild(button("Resume", () -> control(C2SDroneControl.Action.RESUME)));
            bar.addChild(button("Step", this::step));
            bar.addChild(button("Stop", () -> control(C2SDroneControl.Action.STOP)));
        }
        status.getTextStyle().textAlignVertical(Vertical.CENTER).textShadow(false);
        status.getLayout().flex(1).heightPercent(100);
        status.textStyle(s -> s.textColor(ColorPattern.WHITE.color));
        bar.addChild(status);
        return bar;
    }

    private Button button(String label, Runnable onClick) {
        Button b = new Button().setText(label);
        b.getLayout().height(12).width(38);
        b.setOnClick(e -> onClick.run());
        return b;
    }

    /** Load a graph into the editor and drop the built-in save button (we upload via the control bar). */
    private void loadIntoEditor(DroneGraph graph) {
        editorView.loadGraph(graph, this::onEditorSaved);
        editorView.saveButton.removeSelf();
        markGraphSynced(); // a freshly loaded graph matches the server — not dirty
    }

    /** Snapshot the current editor graph as the baseline for unsaved-change detection. */
    private void markGraphSynced() {
        lastSyncedTag = editorView.serializeGraph();
    }

    /** Whether the editor graph differs from what was last sent to / loaded from the server. */
    private boolean isGraphDirty() {
        return !editorView.serializeGraph().equals(lastSyncedTag);
    }

    /** Upload the current editor graph to the server block entity (owner-gated server-side). */
    private void upload() {
        editorView.notifySaved(); // → onEditorSaved
    }

    /** Editor "save" callback: persist the serialized graph onto the server block entity. */
    private void onEditorSaved(CompoundTag tag) {
        ClientPacketDistributor.sendToServer(new C2SSaveDroneProgram(stationPos, tag));
        lastSyncedTag = tag.copy(); // now in sync with the server
    }

    /** Upload the latest graph then start a continuous run (runs until Stop/Pause or the program ends). */
    private void run() {
        editorView.notifySaved();
        ClientPacketDistributor.sendToServer(
                new C2SDroneControl(stationPos, C2SDroneControl.Action.RUN, editorView.serializeGraph()));
    }

    /** Single-step: carry the current graph so a step from idle uploads + starts the program paused. */
    private void step() {
        editorView.notifySaved();
        ClientPacketDistributor.sendToServer(
                new C2SDroneControl(stationPos, C2SDroneControl.Action.STEP, editorView.serializeGraph()));
    }

    private void control(C2SDroneControl.Action action) {
        ClientPacketDistributor.sendToServer(new C2SDroneControl(stationPos, action, new CompoundTag()));
    }

    /** Dock the Log Panel into the editor's graph view (bottom-left corner, the free dock slot). */
    private void installLogPanel() {
        var graphView = editorView.graphView;
        GraphPanel panel = new GraphPanel(graphView, logPanel);
        graphView.getPanelLayer().addChild(panel);
        graphView.dockManager.register(panel, DockSlot.BOTTOM_LEFT);
    }

    private void onTick() {
        pollFlyKeys();
        loadProgramOnce();
        RunState state = runState();
        boolean running = state == RunState.RUNNING || state == RunState.PAUSED;
        readOnly = running; // read by the capture-phase guards
        applyHighlight(running ? stringSync(sync.currentNode()) : "");
        logPanel.setLog(stringSync(sync.log()));
        refreshStatus(state);
    }

    /** Load the server-stored program into the editor the first time a non-empty one arrives. */
    private void loadProgramOnce() {
        if (programLoaded) return;
        Tag tag = sync.program().getSyncValue().getValue();
        if (!(tag instanceof CompoundTag program) || program.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        loadIntoEditor(DroneGraphCodec.fromTag(program, mc.level.registryAccess()));
        programLoaded = true;
        // a reload resets selection — force the highlight to re-resolve next tick
        highlightUid = "";
    }

    /** Select the node currently executing on the server (empty UID clears the selection). */
    private void applyHighlight(String uid) {
        if (uid.equals(highlightUid)) return;
        highlightUid = uid;
        editorView.graphView.clearAllSelected();
        if (uid.isEmpty()) return;
        Graph graph = editorView.getGraph();
        if (graph == null) return;
        try {
            var model = graph.graphModel.getModel(UUID.fromString(uid));
            if (model != null) {
                editorView.graphView.addSelected(model);
            }
        } catch (IllegalArgumentException ignored) {
            // malformed UID — nothing to highlight
        }
    }

    private void refreshStatus(RunState state) {
        int color = switch (state) {
            case RUNNING -> ColorPattern.GREEN.color;
            case PAUSED -> ColorPattern.YELLOW.color;
            case FINISHED -> ColorPattern.CYAN.color;
            default -> ColorPattern.BLACK.color;
        };
        status.setText(Component.literal(String.format("%s  ·  tick %d  ·  score %d",
                state, intSync(sync.runTick()), intSync(sync.score()))));
        status.textStyle(s -> s.textColor(color));
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

    /**
     * Build the default starter program: a nested {@code For x (0..8) { For z (0..8) { … } }} sweep that
     * visits every cell of the fixed 9×9 field by {@link MoveToCoordNode absolute coordinate}, scans the
     * cell, and tends it — plant if plantable, else harvest if ripe, else clear if rotten — logging the
     * cell's merge size each step. A complete, working example a player can tweak.
     *
     * <p>(Reconstruction of the user-authored graph: the cosmetic wire portals "pos x"/"pos z"/"- next"
     * are flattened into direct wires here — behaviourally identical.)</p>
     */
    private static DroneGraph newProgram() {
        DroneGraph graph = new DroneGraph();
        CustomGraphModelImpl gm = graph.graphModel;

        NodeModel entry = gm.createNodeModel(new EntryNode(), new Vector2f(-240, -64));
        NodeModel forX = gm.createNodeModel(new ForNode(), new Vector2f(-120, -64));
        NodeModel forZ = gm.createNodeModel(new ForNode(), new Vector2f(40, -64));
        NodeModel move = gm.createNodeModel(new MoveToCoordNode(), new Vector2f(220, -64));
        NodeModel print = gm.createNodeModel(new DronePrintNode(), new Vector2f(380, -64));
        NodeModel scan = gm.createNodeModel(new ScanCellNode(), new Vector2f(40, 200));
        NodeModel bPlant = gm.createNodeModel(new BranchNode(), new Vector2f(540, -64));
        NodeModel bRipe = gm.createNodeModel(new BranchNode(), new Vector2f(540, 120));
        NodeModel bRotten = gm.createNodeModel(new BranchNode(), new Vector2f(540, 300));
        NodeModel plant = gm.createNodeModel(new PlantNode(), new Vector2f(720, -64));
        NodeModel harvest = gm.createNodeModel(new HarvestNode(), new Vector2f(720, 120));
        NodeModel clear = gm.createNodeModel(new ClearNode(), new Vector2f(720, 300));

        setConstant(forX, "count", DroneField.SIZE);
        setConstant(forZ, "count", DroneField.SIZE);

        // exec: Entry → For x → (body) For z → (body) MoveTo → Print → Branch(plantable)
        wireExec(gm, entry, "next", forX, "in");
        wireExec(gm, forX, "body", forZ, "in");
        wireExec(gm, forZ, "body", move, "trigger");
        wireExec(gm, move, "next", print, "trigger");
        wireExec(gm, print, "next", bPlant, "in");
        // plantable? plant : (check ripe?) harvest : (check rotten?) clear : nothing (iteration ends)
        wireExec(gm, bPlant, "trueExec", plant, "trigger");
        wireExec(gm, bPlant, "falseExec", bRipe, "in");
        wireExec(gm, bRipe, "trueExec", harvest, "trigger");
        wireExec(gm, bRipe, "falseExec", bRotten, "in");
        wireExec(gm, bRotten, "trueExec", clear, "trigger");

        // data: target cell = (forX.index, forZ.index); scan that cell (relative dx=dz=0 after the move)
        wireData(gm, forX, "index", move, "x");
        wireData(gm, forZ, "index", move, "z");
        wireData(gm, scan, "plantable", bPlant, "cond");
        wireData(gm, scan, "ripe", bRipe, "cond");
        wireData(gm, scan, "rotten", bRotten, "cond");
        wireData(gm, scan, "mergeSize", print, "value");
        return graph;
    }

    private static void setConstant(NodeModel node, String inputId, Object value) {
        var c = node.getInputConstantsById().get(inputId);
        if (c != null) c.setValue(value);
    }

    private static void wireExec(CustomGraphModelImpl gm, NodeModel from, String fromId,
                                 NodeModel to, String toId) {
        gm.createWire(to.getInputsById().get(toId), from.getOutputsById().get(fromId));
    }

    private static void wireData(CustomGraphModelImpl gm, NodeModel from, String fromId,
                                 NodeModel to, String toId) {
        gm.createWire(to.getInputsById().get(toId), from.getOutputsById().get(fromId));
    }
}
