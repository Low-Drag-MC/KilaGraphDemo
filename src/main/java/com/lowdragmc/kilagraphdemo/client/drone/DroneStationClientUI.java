package com.lowdragmc.kilagraphdemo.client.drone;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.WhileNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.NotNode;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.drone.DroneMenuSync;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraph;
import com.lowdragmc.kilagraphdemo.drone.graph.DroneGraphCodec;
import com.lowdragmc.kilagraphdemo.drone.node.HarvestNode;
import com.lowdragmc.kilagraphdemo.drone.node.MoveNode;
import com.lowdragmc.kilagraphdemo.drone.node.PlantNode;
import com.lowdragmc.kilagraphdemo.drone.node.ScanCellNode;
import com.lowdragmc.kilagraphdemo.drone.node.WaitNode;
import com.lowdragmc.kilagraphdemo.farm.RunState;
import com.lowdragmc.kilagraphdemo.network.C2SDroneControl;
import com.lowdragmc.kilagraphdemo.network.C2SSaveDroneProgram;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SimpleBinding;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
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
        DroneMenuSync.Bindings sync = DroneMenuSync.register(ui, true, be);

        new DroneStationClientUI(holder.pos, owner, sync).buildInto(root);
        return ui;
    }

    private void buildInto(UIElement root) {
        // Root is a transparent full-screen row: the left half holds the editor panel, the right half is
        // left empty so the world (the farm board) shows through behind the menu screen — the menu's
        // ModularUIContainerScreen draws no background of its own.
        root.getLayout().widthPercent(100).heightPercent(100).flexDirection(FlexDirection.ROW);

        UIElement leftPanel = new UIElement().addClass("panel_bg");
        leftPanel.getLayout().widthPercent(50).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(2);
        root.addChild(leftPanel);

        // Right half: empty + transparent, so the world is visible (R4.3 adds free-fly camera control here).
        worldPanel.getLayout().widthPercent(50).heightPercent(100);
        root.addChild(worldPanel);

        // Open with a placeholder; the stored program loads once it arrives over the sync channel.
        loadIntoEditor(newProgram());

        leftPanel.addChild(controlBar());

        editorView.getLayout().widthPercent(100).flex(1);
        leftPanel.addChild(editorView);
        installReadOnlyGuard();
        installLogPanel();

        // Fly the render camera over the station while the UI is open; restore it when the screen closes
        // (REMOVED fires via ModularUI.onRemoved from Screen.removed()).
        activateCamera();
        installFlyControls(root);
        root.addEventListener(UIEvents.REMOVED, e -> CameraOverrideManager.INSTANCE.deactivate());

        root.addEventListener(UIEvents.TICK, e -> onTick());
        refreshStatus(RunState.IDLE);
    }

    /** Point the override camera above and just south of the station, looking down over the field. */
    private void activateCamera() {
        Vec3 center = Vec3.atCenterOf(stationPos);
        Vec3 camPos = center.add(0, 10, 6); // 10 blocks up, 6 south
        CameraOverrideManager.INSTANCE.activate(camPos, 180f, 55f, center, FLY_RADIUS); // face north, look down
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
        String[] editingEvents = {
                UIEvents.MOUSE_DOWN, UIEvents.MOUSE_UP, UIEvents.MOUSE_WHEEL,
                UIEvents.KEY_DOWN, UIEvents.EXECUTE_COMMAND, UIEvents.DRAG_PERFORM,
        };
        for (String type : editingEvents) {
            editorView.addEventListener(type, e -> {
                if (readOnly) {
                    e.stopPropagation();
                }
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
    }

    /** Upload the current editor graph to the server block entity (owner-gated server-side). */
    private void upload() {
        editorView.notifySaved(); // → onEditorSaved
    }

    /** Editor "save" callback: persist the serialized graph onto the server block entity. */
    private void onEditorSaved(CompoundTag tag) {
        ClientPacketDistributor.sendToServer(new C2SSaveDroneProgram(stationPos, tag));
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
     * Build the default starter program: {@code Entry → Move(north) → Plant → While(not ripe){ Wait } →
     * Harvest}. The {@code While} re-scans the drone's current cell each iteration and waits until the
     * pumpkin ripens, then harvests — a complete, working example a player can tweak.
     */
    private static DroneGraph newProgram() {
        DroneGraph graph = new DroneGraph();
        CustomGraphModelImpl gm = graph.graphModel;

        NodeModel entry = gm.createNodeModel(new EntryNode(), new Vector2f(0, 0));
        NodeModel move = gm.createNodeModel(new MoveNode(), new Vector2f(160, 0));
        NodeModel plant = gm.createNodeModel(new PlantNode(), new Vector2f(320, 0));
        NodeModel whileNode = gm.createNodeModel(new WhileNode(), new Vector2f(480, 0));
        NodeModel wait = gm.createNodeModel(new WaitNode(), new Vector2f(480, 140));
        NodeModel scan = gm.createNodeModel(new ScanCellNode(), new Vector2f(160, 220));
        NodeModel not = gm.createNodeModel(new NotNode(), new Vector2f(320, 220));
        NodeModel harvest = gm.createNodeModel(new HarvestNode(), new Vector2f(680, 0));

        // exec chain
        wireExec(gm, entry, "next", move, "trigger");
        wireExec(gm, move, "next", plant, "trigger");
        wireExec(gm, plant, "next", whileNode, "in");
        wireExec(gm, whileNode, "body", wait, "trigger");      // loop while not ripe: wait
        wireExec(gm, whileNode, "completed", harvest, "trigger"); // ripe: harvest

        // data: while.cond = NOT(scan(current cell).ripe)
        wireData(gm, scan, "ripe", not, "in");
        wireData(gm, not, "out", whileNode, "cond");
        return graph;
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
