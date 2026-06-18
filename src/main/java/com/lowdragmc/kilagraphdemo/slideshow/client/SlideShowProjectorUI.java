package com.lowdragmc.kilagraphdemo.slideshow.client;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import com.lowdragmc.kilagraphdemo.graph.LocalGraphStore;
import com.lowdragmc.kilagraphdemo.graph.ServerWorkEntry;
import com.lowdragmc.kilagraphdemo.graph.WorkMeta;
import com.lowdragmc.kilagraphdemo.graph.WorkPackage;
import com.lowdragmc.kilagraphdemo.network.C2SSetProjectorGraph;
import com.lowdragmc.kilagraphdemo.slideshow.IProjectorGraphHolder;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraph;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraphResource;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The SlideShow projector browser. Left column lists the player's local SlideShow graph works (authoring:
 * New / Edit / Upload / Delete) and the server's shared works (Download). The right panel shows the selected
 * work and a "Use on this projector" action that sends {@link C2SSetProjectorGraph} — applied to the
 * projector, persisted, and synced to all clients (which lazily download the work when rendering it).
 *
 * <p>Only the SlideShow {@link WorkMeta#KIND_SLIDESHOW kind} of work is shown. References the projector only
 * through {@link IProjectorGraphHolder}, never SlideShow types.</p>
 */
public class SlideShowProjectorUI implements ClientWorks.Listener {

    private final GlobalPos blockPos;

    @Getter
    private final UIElement root = new UIElement();
    private final UIElement serverListContainer = new UIElement();
    private final UIElement localListContainer = new UIElement();
    private final UIElement metaLabels = new UIElement();
    private final UIElement body = new UIElement();
    private final UIElement actions = new UIElement();
    private final TextField titleField = new TextField();
    private final TextArea descriptionField = new TextArea();

    private final Map<String, UIElement> serverRows = new HashMap<>();
    private final Map<String, UIElement> localRows = new HashMap<>();

    @Nullable
    private ServerWorkEntry selectedServer;
    @Nullable
    private WorkMeta selectedLocal;
    /** The uid currently used by this projector, tracked locally for the "(in use)" marker + instant feedback. */
    private String currentUid;

    public SlideShowProjectorUI(GlobalPos blockPos) {
        this.blockPos = blockPos;
        this.currentUid = resolveCurrentUid();
        build();
        ClientWorks.setListener(this);
        ClientWorks.requestList();
    }

    private String resolveCurrentUid() {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(blockPos.pos()) instanceof IProjectorGraphHolder holder) {
            return holder.kilagraphdemo$getGraphWorkUid();
        }
        return "";
    }

    private void build() {
        root.addClass("panel_bg");
        root.getLayout().width(340).height(220).flexDirection(FlexDirection.ROW).gapAll(4).paddingAll(4);

        UIElement left = new UIElement();
        left.getLayout().flexDirection(FlexDirection.COLUMN).heightPercent(100).width(160).gapAll(2);
        left.addChild(sectionLabel("Local works"));
        left.addChild(new Button().setText("New").setOnClick(e -> onNew()));
        left.addChild(listView(localListContainer));
        left.addChild(sectionLabel("Server works"));
        left.addChild(listView(serverListContainer));

        UIElement right = new UIElement();
        right.getLayout().flexDirection(FlexDirection.COLUMN).flex(1).heightPercent(100).gapAll(3);
        metaLabels.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(1);
        body.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(1);
        actions.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(2);
        right.addChild(metaLabels);
        right.addChild(body);
        right.addChild(actions);

        root.addChild(left);
        root.addChild(right);

        refreshLocalList();
        refreshServerList();
        refreshDetail();
    }

    private Label sectionLabel(String text) {
        Label l = new Label();
        l.setText(Component.literal(text));
        l.getLayout().height(9);
        return l;
    }

    private ScrollerView listView(UIElement container) {
        ScrollerView view = new ScrollerView();
        view.getLayout().flex(1).widthPercent(100);
        container.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(1);
        view.addScrollViewChild(container);
        return view;
    }

    // ---- lists -------------------------------------------------------------------------------

    private void refreshLocalList() {
        localListContainer.clearAllChildren();
        localRows.clear();
        List<WorkMeta> metas = new ArrayList<>(LocalGraphStore.list());
        metas.removeIf(m -> !m.isSlideShow());
        metas.sort(Comparator.comparing(WorkMeta::title, String.CASE_INSENSITIVE_ORDER));
        for (WorkMeta m : metas) {
            UIElement row = createRow(m.title(), m.uid(), () -> onSelectLocal(m));
            localRows.put(m.uid(), row);
            localListContainer.addChild(row);
        }
        highlight();
    }

    private void refreshServerList() {
        serverListContainer.clearAllChildren();
        serverRows.clear();
        List<ServerWorkEntry> entries = new ArrayList<>(ClientWorks.serverWorks());
        entries.removeIf(e -> !e.meta().isSlideShow());
        entries.sort(Comparator.comparingLong((ServerWorkEntry e) -> e.meta().firstUploadTime()).reversed());
        for (ServerWorkEntry entry : entries) {
            WorkMeta m = entry.meta();
            String tag = "(" + (m.authorName().isEmpty() ? "?" : m.authorName()) + ")";
            UIElement row = createRow(m.title() + " " + tag, m.uid(), () -> onSelectServer(entry));
            serverRows.put(m.uid(), row);
            serverListContainer.addChild(row);
        }
        highlight();
    }

    private UIElement createRow(String text, String uid, Runnable onClick) {
        boolean inUse = uid.equals(currentUid);
        Label name = new Label();
        name.setText(Component.literal(text + (inUse ? "  (in use)" : "")));
        name.textStyle(style -> {
            style.textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER);
            if (inUse) style.textColor(ColorPattern.GREEN.color);
        });
        name.setOverflowVisible(false);
        name.getLayout().flex(1).heightPercent(100);

        UIElement row = new UIElement();
        row.getLayout().widthPercent(100).height(12).flexDirection(FlexDirection.ROW).paddingHorizontal(2).gapAll(2);
        row.addChild(name);
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> onClick.run());
        return row;
    }

    private void highlight() {
        String localUid = selectedLocal == null ? null : selectedLocal.uid();
        String serverUid = selectedServer == null ? null : selectedServer.meta().uid();
        localRows.forEach((u, row) -> setSelected(row, u.equals(localUid)));
        serverRows.forEach((u, row) -> setSelected(row, u.equals(serverUid)));
    }

    private void setSelected(UIElement row, boolean on) {
        row.style(style -> style.backgroundTexture(on ? ColorPattern.LIGHT_BLUE.rectTexture() : IGuiTexture.EMPTY));
    }

    // ---- selection / detail ------------------------------------------------------------------

    private void onSelectLocal(WorkMeta m) {
        selectedLocal = m;
        selectedServer = null;
        highlight();
        refreshDetail();
    }

    private void onSelectServer(ServerWorkEntry entry) {
        selectedServer = entry;
        selectedLocal = null;
        highlight();
        refreshDetail();
    }

    private void refreshDetail() {
        metaLabels.clearAllChildren();
        body.clearAllChildren();
        actions.clearAllChildren();

        if (selectedLocal != null) {
            buildLocalDetail(selectedLocal);
        } else if (selectedServer != null) {
            buildServerDetail(selectedServer);
        } else {
            metaLabels.addChild(metaLabel("Select a work, or create a New one", ColorPattern.WHITE.color));
        }

        // "Clear" is always available when this projector currently uses a graph.
        if (!currentUid.isEmpty()) {
            actions.addChild(new Button().setText("Clear from projector").setOnClick(e -> useOnProjector("")));
        }
    }

    private void buildLocalDetail(WorkMeta m) {
        metaLabels.addChild(metaLabel("Local work", ColorPattern.YELLOW.color));
        titleField.setText(m.title());
        body.addChild(sectionLabel("Title:"));
        body.addChild(titleField);
        descriptionField.setLines(Arrays.asList(m.description().split("\n", -1)));
        body.addChild(sectionLabel("Description:"));
        body.addChild(descriptionField);

        actions.addChild(new Button().setText("Edit").setOnClick(e -> onEdit(m)));
        actions.addChild(new Button().setText("Save meta").setOnClick(e -> onSaveMeta(m)));
        actions.addChild(new Button().setText("Upload").setOnClick(e -> onUpload(m)));
        if (serverHasUid(m.uid())) {
            actions.addChild(new Button().setText("Use on this projector").setOnClick(e -> useOnProjector(m.uid())));
        }
        actions.addChild(new Button().setText("Delete (local)").setOnClick(e -> onDeleteLocal(m.uid())));
    }

    private void buildServerDetail(ServerWorkEntry entry) {
        WorkMeta m = entry.meta();
        metaLabels.addChild(metaLabel(m.title(), ColorPattern.YELLOW.color));
        metaLabels.addChild(metaLabel("by " + (m.authorName().isEmpty() ? "?" : m.authorName()), ColorPattern.CYAN.color));
        body.addChild(descriptionScroller(m.description()));

        actions.addChild(new Button().setText("Use on this projector").setOnClick(e -> useOnProjector(m.uid())));
        actions.addChild(new Button().setText("Download (edit locally)").setOnClick(e -> ClientWorks.download(m.uid())));
        // Author may delete their own; Creative/Op may delete anyone's.
        if (isMineServer(m) || canEdit()) {
            actions.addChild(new Button().setText("Delete from server").setOnClick(e -> onDeleteServer(m.uid())));
        }
    }

    /** Whether the local player may delete others' server works (Creative or Op = gamemaster). */
    private boolean canEdit() {
        var p = Minecraft.getInstance().player;
        return p != null && (p.isCreative() || p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    private boolean isMineServer(WorkMeta m) {
        var p = Minecraft.getInstance().player;
        return p != null && m.authorUuid().equals(p.getUUID().toString());
    }

    private void onDeleteServer(String uid) {
        if (uid.equals(currentUid)) useOnProjector(""); // a deleted work must not stay displayed
        ClientWorks.delete(uid);
        if (selectedServer != null && selectedServer.meta().uid().equals(uid)) selectedServer = null;
        refreshDetail();
    }

    private Label metaLabel(String text, int color) {
        Label l = new Label();
        l.setText(Component.literal(text));
        l.textStyle(style -> style.textColor(color).textShadow(false)
                .textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER));
        l.setOverflowVisible(false);
        l.getLayout().widthPercent(100).height(9);
        return l;
    }

    private UIElement descriptionScroller(String text) {
        ScrollerView sv = new ScrollerView();
        sv.getLayout().widthPercent(100).height(60);
        Label l = new Label();
        l.setText(Component.literal(text.isEmpty() ? "(no description)" : text));
        l.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
        l.getLayout().widthPercent(100);
        sv.addScrollViewChild(l);
        return sv;
    }

    // ---- actions -----------------------------------------------------------------------------

    private void onNew() {
        WorkMeta meta = WorkMeta.newLocal("Untitled", WorkMeta.KIND_SLIDESHOW);
        LocalGraphStore.save(new WorkPackage(meta, SlideShowGraphResource.INSTANCE.serializeGraph(new SlideShowGraph()),
                com.lowdragmc.kilagraphdemo.graph.ModelSelection.DEFAULT));
        refreshLocalList();
        onSelectLocal(meta);
    }

    private void onEdit(WorkMeta meta) {
        LocalGraphStore.load(meta.uid()).ifPresent(pkg -> {
            SlideShowGraph graph = pkg.loadGraph(SlideShowGraphResource.INSTANCE::deserializeGraph);
            SlideShowScreens.openEditor(graph, (tag, resources) -> {
                LocalGraphStore.save(new WorkPackage(meta, tag,
                        com.lowdragmc.kilagraphdemo.graph.ModelSelection.DEFAULT, resources));
                refreshDetail();
            });
        });
    }

    /** Persist edited title/description back to the local work. */
    private void onSaveMeta(WorkMeta meta) {
        LocalGraphStore.load(meta.uid()).ifPresent(pkg -> {
            String title = titleField.getValue();
            String desc = String.join("\n", descriptionField.getValue());
            WorkMeta updated = meta.withTitle(title.isBlank() ? "Untitled" : title).withDescription(desc);
            LocalGraphStore.save(pkg.withMeta(updated));
            selectedLocal = updated;
            refreshLocalList();
            refreshDetail();
        });
    }

    private void onUpload(WorkMeta meta) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        // Apply the current title/description fields, and require a non-empty description before uploading.
        String title = titleField.getValue();
        String desc = String.join("\n", descriptionField.getValue());
        if (desc.isBlank()) {
            Dialog.showNotification("Description required",
                    "Please write a description before uploading.", null).show(root);
            return;
        }
        String myUuid = player.getUUID().toString();
        boolean mineWork = meta.authorUuid().isEmpty() || meta.authorUuid().equals(myUuid);
        boolean willCreateNew = !(mineWork && serverHasUid(meta.uid()));
        if (willCreateNew && !Kilagraphdemo.canBypassUploadLimit(player)
                && myServerWorkCount() >= Kilagraphdemo.MAX_WORKS_PER_PLAYER) {
            Dialog.showNotification("Upload limit reached",
                    "You can publish at most " + Kilagraphdemo.MAX_WORKS_PER_PLAYER + " works.", null).show(root);
            return;
        }
        String finalTitle = title.isBlank() ? "Untitled" : title;
        LocalGraphStore.load(meta.uid()).ifPresent(pkg -> {
            String author = pkg.meta().authorUuid();
            boolean mine = author.isEmpty() || author.equals(myUuid);
            // Mine: keep uid, update title/desc. Someone else's downloaded copy: fork into a new work.
            WorkMeta newMeta = mine
                    ? pkg.meta().withTitle(finalTitle).withDescription(desc)
                    : WorkMeta.newLocal(finalTitle, WorkMeta.KIND_SLIDESHOW).withDescription(desc);
            WorkPackage toUpload = pkg.withMeta(newMeta);
            LocalGraphStore.save(toUpload);
            refreshLocalList();
            onSelectLocal(toUpload.meta());
            ClientWorks.upload(toUpload);
            Dialog.showNotification("Uploaded", "Shared to the server.", null).show(root);
        });
    }

    private void onDeleteLocal(String uid) {
        if (uid.equals(currentUid)) useOnProjector(""); // a deleted work must not stay displayed
        LocalGraphStore.delete(uid);
        if (selectedLocal != null && selectedLocal.uid().equals(uid)) selectedLocal = null;
        refreshLocalList();
        refreshDetail();
    }

    /** Send the new graph selection to the server; optimistically update the local "(in use)" marker. */
    private void useOnProjector(String uid) {
        ClientPacketDistributor.sendToServer(new C2SSetProjectorGraph(blockPos.pos(), uid));
        currentUid = uid;
        refreshLocalList();
        refreshServerList();
        refreshDetail();
    }

    private boolean serverHasUid(String uid) {
        return ClientWorks.serverWorks().stream().anyMatch(e -> e.meta().uid().equals(uid));
    }

    private int myServerWorkCount() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        String myUuid = player.getUUID().toString();
        return (int) ClientWorks.serverWorks().stream()
                .filter(e -> e.meta().isSlideShow())
                .filter(e -> e.meta().authorUuid().equals(myUuid)).count();
    }

    // ---- ClientWorks.Listener ----------------------------------------------------------------

    @Override
    public void onWorksUpdated(@Nullable String downloadedUid) {
        if (selectedServer != null) {
            String uid = selectedServer.meta().uid();
            selectedServer = ClientWorks.serverWorks().stream()
                    .filter(en -> en.meta().uid().equals(uid)).findFirst().orElse(null);
        }
        refreshLocalList();
        refreshServerList();
        refreshDetail();
    }
}
