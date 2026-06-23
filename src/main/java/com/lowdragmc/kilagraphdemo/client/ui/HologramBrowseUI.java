package com.lowdragmc.kilagraphdemo.client.ui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.block.HologramBlockEntity;
import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import com.lowdragmc.kilagraphdemo.client.editor.HologramScreens;
import com.lowdragmc.kilagraphdemo.client.editor.LocalShaderFunctions;
import com.lowdragmc.kilagraphdemo.client.editor.ModelBundler;
import com.lowdragmc.kilagraphdemo.client.editor.PlacementDialog;
import com.lowdragmc.kilagraphdemo.client.editor.TextureBundler;
import com.lowdragmc.kilagraphdemo.client.editor.WorkValidator;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplays;
import com.lowdragmc.kilagraphdemo.client.render.HologramPlacements;
import com.lowdragmc.kilagraphdemo.graph.LocalGraphStore;
import com.lowdragmc.kilagraphdemo.graph.ModelSelection;
import com.lowdragmc.kilagraphdemo.graph.ServerWorkEntry;
import com.lowdragmc.kilagraphdemo.graph.WorkMeta;
import com.lowdragmc.kilagraphdemo.graph.WorkPackage;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The hologram browser. Left column has two lists — works shared on the server (downloadable) and the
 * player's local works. The right panel shows the selected work's details and contextual actions:
 * download / like for a server work; edit / display / upload / delete for a local work. Local rename via
 * double-click. The hologram remembers (client-only) the work it shows, so it's default-selected.
 */
public class HologramBrowseUI implements ClientWorks.Listener {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GlobalPos blockPos;
    @Nullable
    private final HologramBlockEntity blockEntity;

    @Getter
    private final UIElement root = new UIElement();
    private final UIElement serverListContainer = new UIElement();
    private final UIElement localListContainer = new UIElement();
    // Right-panel structure: a header row (meta labels left, author avatar right), a body (description),
    // and the action buttons — each cleared + rebuilt per selection in refreshDetail.
    private final UIElement metaLabels = new UIElement();
    private final UIElement avatarHolder = new UIElement();
    private final UIElement body = new UIElement();
    private final UIElement actions = new UIElement();
    private final TextArea descriptionField = new TextArea();
    private final AuthorAvatarView authorAvatar = new AuthorAvatarView();

    private final Map<String, UIElement> serverRows = new HashMap<>();
    private final Map<String, UIElement> localRows = new HashMap<>();

    // Selection (server XOR local).
    @Nullable
    private ServerWorkEntry selectedServer;
    @Nullable
    private WorkMeta selectedLocal;
    @Nullable
    private HologramDisplay workingDisplay;
    private ModelSelection model = ModelSelection.DEFAULT;
    private SortMode sortMode = SortMode.LIKES_THEN_NEW;
    private boolean mineOnly = false;
    @Nullable
    private Button mineButton;

    /** Server-list ordering. Default blends likes then recency; the toggles pick a single criterion. */
    private enum SortMode {
        LIKES_THEN_NEW(Comparator.comparingInt(ServerWorkEntry::likeCount).reversed()
                .thenComparing(Comparator.comparingLong((ServerWorkEntry e) -> e.meta().firstUploadTime()).reversed())),
        MOST_LIKED(Comparator.comparingInt(ServerWorkEntry::likeCount).reversed()),
        NEWEST(Comparator.comparingLong((ServerWorkEntry e) -> e.meta().firstUploadTime()).reversed()),
        OLDEST(Comparator.comparingLong((ServerWorkEntry e) -> e.meta().firstUploadTime()));

        final Comparator<ServerWorkEntry> comparator;

        SortMode(Comparator<ServerWorkEntry> comparator) {
            this.comparator = comparator;
        }
    }

    public HologramBrowseUI(GlobalPos blockPos, @Nullable HologramBlockEntity blockEntity) {
        this.blockPos = blockPos;
        this.blockEntity = blockEntity;
        build();
        ClientWorks.setListener(this);
        ClientWorks.requestList();
    }

    private void build() {
        root.addClass("panel_bg");
        root.getLayout().width(360).height(244).flexDirection(FlexDirection.ROW).gapAll(4).paddingAll(4);

        // Left column: server list (top), local list (bottom), New button.
        UIElement left = new UIElement();
        left.getLayout().flexDirection(FlexDirection.COLUMN).heightPercent(100).width(150).gapAll(2);
        left.addChild(sectionLabel("kilagraphdemo.ui.hologram.section_server"));
        left.addChild(sortRow());
        left.addChild(listView(serverListContainer));
        left.addChild(sectionLabel("kilagraphdemo.ui.hologram.section_local"));
        left.addChild(listView(localListContainer));
        left.addChild(new Button().setText("kilagraphdemo.ui.hologram.new").setOnClick(e -> onNew())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.new.tooltip")));

        // Right column: meta header row (labels + avatar), body (description), action buttons.
        UIElement right = new UIElement();
        right.getLayout().flexDirection(FlexDirection.COLUMN).flex(1).heightPercent(100).gapAll(3);

        UIElement metaRow = new UIElement();
        metaRow.getLayout().flexDirection(FlexDirection.ROW).widthPercent(100).gapAll(3);
        metaLabels.getLayout().flexDirection(FlexDirection.COLUMN).flex(1).gapAll(1);
        avatarHolder.getLayout().width(72).height(72);
        metaRow.addChild(metaLabels);
        metaRow.addChild(avatarHolder);

        body.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(1);
        actions.getLayout().flexDirection(FlexDirection.COLUMN).widthPercent(100).gapAll(2);
        descriptionField.getLayout().widthPercent(100).height(44);

        right.addChild(metaRow);
        right.addChild(body);
        right.addChild(actions);

        root.addChild(left);
        root.addChild(right);

        refreshLocalList();
        refreshServerList();

        // Default-select whatever this hologram currently displays.
        String displayed = blockEntity == null ? null : blockEntity.getDisplayedWorkUid();
        if (displayed != null) {
            LocalGraphStore.list().stream().filter(m -> m.uid().equals(displayed)).findFirst()
                    .ifPresent(this::onSelectLocal);
        }
    }

    /** A compact row of server-list sort toggles. */
    private UIElement sortRow() {
        UIElement row = new UIElement();
        row.getLayout().flexDirection(FlexDirection.ROW).widthPercent(100).gapAll(1).height(12);
        row.addChild(sortButton("kilagraphdemo.ui.hologram.sort_liked",
                "kilagraphdemo.ui.hologram.sort_liked.tooltip", SortMode.MOST_LIKED));
        row.addChild(sortButton("kilagraphdemo.ui.hologram.sort_new",
                "kilagraphdemo.ui.hologram.sort_new.tooltip", SortMode.NEWEST));
        row.addChild(sortButton("kilagraphdemo.ui.hologram.sort_old",
                "kilagraphdemo.ui.hologram.sort_old.tooltip", SortMode.OLDEST));
        row.addChild(mineFilterButton());
        return row;
    }

    /** Toggle button limiting the server list to works uploaded by this client. */
    private Button mineFilterButton() {
        mineButton = new Button().setText(mineLabel());
        mineButton.getLayout().flex(1).heightPercent(100);
        mineButton.style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.mine.tooltip"));
        mineButton.setOnClick(e -> {
            mineOnly = !mineOnly;
            mineButton.setText(mineLabel());
            refreshServerList();
        });
        return mineButton;
    }

    private Component mineLabel() {
        return Component.translatable(mineOnly ? "kilagraphdemo.ui.hologram.mine_on" : "kilagraphdemo.ui.hologram.mine");
    }

    private Button sortButton(String labelKey, String tooltipKey, SortMode mode) {
        Button button = new Button().setText(labelKey);
        button.getLayout().flex(1).heightPercent(100);
        button.style(s -> s.appendTooltipsString(tooltipKey));
        button.setOnClick(e -> {
            sortMode = mode;
            refreshServerList();
        });
        return button;
    }

    private Label sectionLabel(String key) {
        Label l = new Label();
        l.setText(Component.translatable(key));
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

    private void refreshServerList() {
        serverListContainer.clearAllChildren();
        serverRows.clear();
        List<ServerWorkEntry> entries = new ArrayList<>(ClientWorks.serverWorks());
        entries.removeIf(e -> e.meta().isSlideShow()); // SlideShow graph works belong to the projector browser
        if (mineOnly) entries.removeIf(e -> !isMine(e));
        entries.sort(sortMode.comparator);
        int index = 1;
        for (ServerWorkEntry entry : entries) {
            UIElement row = createServerRow(entry, index++);
            serverRows.put(entry.meta().uid(), row);
            serverListContainer.addChild(row);
        }
        highlight();
    }

    /**
     * A server-list row: "{n}." index, the work name + author tag (flex-1, roll-on-hover, clipped), and
     * the like count at the end. Works you uploaded are tagged "(yours)" and coloured for recognition.
     */
    private UIElement createServerRow(ServerWorkEntry entry, int index) {
        WorkMeta m = entry.meta();
        boolean mine = isMine(entry);

        Label idx = new Label();
        idx.setText(Component.literal(index + "."));
        idx.textStyle(style -> style.textAlignVertical(Vertical.CENTER).textColor(ColorPattern.WHITE.color));
        idx.getLayout().width(12).heightPercent(100);

        Component authorTag = mine ? Component.translatable("kilagraphdemo.ui.hologram.yours")
                : Component.literal("(" + (m.authorName().isEmpty() ? "?" : m.authorName()) + ")");
        Label name = new Label();
        name.setText(Component.literal(m.title() + " ").append(authorTag));
        name.textStyle(style -> {
            style.textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER);
            if (mine) style.textColor(ColorPattern.YELLOW.color);
        });
        name.setOverflowVisible(false);
        name.getLayout().flex(1).heightPercent(100);

        Label likes = new Label();
        likes.setText(Component.literal("♥ " + entry.likeCount()));
        likes.textStyle(style -> style.textAlignVertical(Vertical.CENTER)
                .textAlignHorizontal(Horizontal.RIGHT).textColor(ColorPattern.PINK.color));
        likes.getLayout().width(26).heightPercent(100);

        UIElement row = new UIElement();
        row.getLayout().widthPercent(100).height(12).flexDirection(FlexDirection.ROW).paddingHorizontal(2).gapAll(2);
        row.addChildren(idx, name, likes);
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> onSelectServer(entry));
        return row;
    }

    private void refreshLocalList() {
        localListContainer.clearAllChildren();
        localRows.clear();
        String displayedUid = blockEntity == null ? null : blockEntity.getDisplayedWorkUid();
        for (WorkMeta meta : LocalGraphStore.list()) {
            if (meta.isSlideShow()) continue; // SlideShow graph works belong to the projector browser
            Component title = Component.literal(meta.title()).append(meta.uid().equals(displayedUid)
                    ? Component.translatable("kilagraphdemo.ui.hologram.displayed") : Component.empty());
            UIElement row = createRow(title, () -> onSelectLocal(meta), meta);
            localRows.put(meta.uid(), row);
            localListContainer.addChild(row);
        }
        highlight();
    }

    /** A list row: vertically-centred, roll-on-hover label; click selects. Local rows rename on double-click. */
    private UIElement createRow(Component title, Runnable onClick, @Nullable WorkMeta localMeta) {
        Label label = new Label();
        label.setText(title);
        label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER));
        label.setOverflowVisible(false);
        label.getLayout().widthPercent(100).heightPercent(100);
        UIElement row = new UIElement();
        row.getLayout().widthPercent(100).height(12).paddingHorizontal(2);
        row.addChild(label);
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> onClick.run());
        if (localMeta != null) {
            row.addEventListener(UIEvents.DOUBLE_CLICK, e -> startRename(localMeta, row, label));
        }
        return row;
    }

    private void highlight() {
        String serverUid = selectedServer == null ? null : selectedServer.meta().uid();
        String localUid = selectedLocal == null ? null : selectedLocal.uid();
        serverRows.forEach((uid, row) -> setSelected(row, uid.equals(serverUid)));
        localRows.forEach((uid, row) -> setSelected(row, uid.equals(localUid)));
    }

    private void setSelected(UIElement row, boolean on) {
        row.style(style -> style.backgroundTexture(on ? ColorPattern.LIGHT_BLUE.rectTexture() : IGuiTexture.EMPTY));
    }

    // ---- selection ---------------------------------------------------------------------------

    private void onSelectServer(ServerWorkEntry entry) {
        selectedServer = entry;
        selectedLocal = null;
        workingDisplay = null;
        highlight();
        refreshDetail();
    }

    private void onSelectLocal(WorkMeta meta) {
        LocalGraphStore.load(meta.uid()).ifPresent(pkg -> {
            selectedLocal = pkg.meta();
            selectedServer = null;
            model = pkg.model();
            HologramDisplay live = isCurrentlyDisplayed(meta.uid()) ? HologramDisplays.resolve(blockPos) : null;
            workingDisplay = live != null ? live
                    : new HologramDisplay(pkg.loadGraph(), model.toContent(), model.renderRadius());
            highlight();
            refreshDetail();
        });
    }

    private boolean isCurrentlyDisplayed(String uid) {
        return blockEntity != null && uid.equals(blockEntity.getDisplayedWorkUid());
    }

    // ---- detail + actions --------------------------------------------------------------------

    private void refreshDetail() {
        metaLabels.clearAllChildren();
        avatarHolder.clearAllChildren();
        body.clearAllChildren();
        actions.clearAllChildren();
        if (selectedServer != null) {
            buildServerDetail(selectedServer);
        } else if (selectedLocal != null) {
            buildLocalDetail(selectedLocal);
        } else {
            metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.select_work"), ColorPattern.WHITE.color));
        }
    }

    /** A colour-coded, single-line meta label (roll-on-hover so long values are still readable). */
    private Label metaLabel(Component text, int color) {
        Label l = new Label();
        l.setText(text);
        l.textStyle(style -> style.textColor(color).textShadow(false).textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER));
        l.setOverflowVisible(false);
        l.getLayout().widthPercent(100).height(9);
        return l;
    }

    private void buildServerDetail(ServerWorkEntry entry) {
        WorkMeta m = entry.meta();
        // Colour-coded meta labels (one per field) on the left of the header row.
        metaLabels.addChild(metaLabel(Component.literal(m.title()), ColorPattern.YELLOW.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.meta_by",
                m.authorName().isEmpty() ? "?" : m.authorName()), ColorPattern.CYAN.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.meta_first", date(m.firstUploadTime())), ColorPattern.GRAY.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.meta_updated", date(m.lastUpdateTime())), ColorPattern.GRAY.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.meta_likes", entry.likeCount()), ColorPattern.PINK.color));
        Optional<Integer> localVer = localVersionOf(m.uid());
        boolean updateAvailable = localVer.isPresent() && localVer.get() < m.version();
        if (updateAvailable) metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.update_available"), ColorPattern.ORANGE.color));

        // Author avatar (3D) on the right of the header row.
        if (!m.authorUuid().isEmpty()) {
            try {
                authorAvatar.setAuthor(UUID.fromString(m.authorUuid()), m.authorName());
                avatarHolder.addChild(authorAvatar);
            } catch (IllegalArgumentException ignored) {
                // malformed uuid — skip the avatar
            }
        }

        // Description (read-only) in a scroller so long text stays contained.
        body.addChild(descriptionScroller(m.description()));

        // Actions.
        if (ClientWorks.isDownloading(m.uid())) {
            int pct = Math.round(ClientWorks.downloadProgress(m.uid()) * 100);
            actions.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.downloading", pct), ColorPattern.GREEN.color));
        } else {
            String download = localVer.isEmpty() ? "kilagraphdemo.ui.hologram.download"
                    : (updateAvailable ? "kilagraphdemo.ui.hologram.update" : "kilagraphdemo.ui.hologram.redownload");
            actions.addChild(new Button().setText(download).setOnClick(e -> ClientWorks.download(m.uid()))
                    .style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.download.tooltip")));
        }
        actions.addChild(new Button().setText(entry.likedByMe() ? "kilagraphdemo.ui.common.unlike" : "kilagraphdemo.ui.common.like")
                .setOnClick(e -> ClientWorks.setLike(m.uid(), !entry.likedByMe()))
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.common.like.tooltip")));
        // Deleting a published work happens here (on the server entry), not on the local copy. The author
        // may delete their own; a Creative/Op player may delete anyone's.
        if (isMine(entry) || (Minecraft.getInstance().player != null
                && Kilagraphdemo.canBypassUploadLimit(Minecraft.getInstance().player))) {
            actions.addChild(new Button().setText("kilagraphdemo.ui.hologram.delete_server")
                    .setOnClick(e -> {
                        clearDisplayIfShowing(m.uid());
                        ClientWorks.delete(m.uid());
                    }).style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.delete_server.tooltip")));
        }
    }

    /** A read-only description in a vertical scroller (wraps + grows; scrolls when long). */
    private UIElement descriptionScroller(String text) {
        ScrollerView sv = new ScrollerView();
        sv.getLayout().widthPercent(100).height(40);
        Label l = new Label();
        l.setText(text.isEmpty() ? Component.translatable("kilagraphdemo.ui.common.no_description") : Component.literal(text));
        l.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
        l.getLayout().widthPercent(100);
        sv.addScrollViewChild(l);
        return sv;
    }

    /** Whether the given server work was uploaded by this client's account (UUID == GameProfile id). */
    private boolean isMine(ServerWorkEntry entry) {
        return Minecraft.getInstance().player != null
                && entry.meta().authorUuid().equals(Minecraft.getInstance().player.getUUID().toString());
    }

    /** Whether a work with this uid is currently published on the server. */
    private boolean serverHasUid(String uid) {
        return ClientWorks.serverWorks().stream().anyMatch(e -> e.meta().uid().equals(uid));
    }

    /** How many of the server's works belong to this client (for the upload-quota pre-check). */
    private int myServerWorkCount() {
        return (int) ClientWorks.serverWorks().stream().filter(this::isMine).count();
    }

    private void buildLocalDetail(WorkMeta m) {
        metaLabels.addChild(metaLabel(Component.literal(m.title()), ColorPattern.YELLOW.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.meta_model", model.describe()), ColorPattern.CYAN.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.hologram.rename_hint"), ColorPattern.GRAY.color));

        // Editable multi-line description (used on upload) in the body.
        descriptionField.setLines(Arrays.asList(m.description().split("\n", -1)));
        body.addChild(sectionLabel("kilagraphdemo.ui.hologram.description"));
        body.addChild(descriptionField);

        actions.addChild(new Button().setText("kilagraphdemo.ui.hologram.edit").setOnClick(e -> onEdit())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.edit.tooltip")));
        actions.addChild(new Button().setText("kilagraphdemo.ui.hologram.display").setOnClick(e -> onDisplay())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.display.tooltip")));
        actions.addChild(new Button().setText("kilagraphdemo.ui.hologram.placement").setOnClick(e -> onPlacement())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.placement.tooltip")));
        actions.addChild(new Button().setText("kilagraphdemo.ui.hologram.upload").setOnClick(e -> onUpload())
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.upload.tooltip")));
        actions.addChild(new Button().setText("kilagraphdemo.ui.hologram.delete_local").setOnClick(e -> onDeleteLocal(m.uid()))
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.hologram.delete_local.tooltip")));
    }

    private static String date(long ms) {
        return ms <= 0 ? "—" : Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(FMT);
    }

    private Optional<Integer> localVersionOf(String uid) {
        return LocalGraphStore.list().stream().filter(m -> m.uid().equals(uid)).map(WorkMeta::version).findFirst();
    }

    // ---- actions -----------------------------------------------------------------------------

    private void onNew() {
        WorkMeta meta = WorkMeta.newLocal("Untitled");
        LocalGraphStore.save(WorkPackage.create(meta, new RenderTypeGraph(), ModelSelection.DEFAULT));
        refreshLocalList();
        onSelectLocal(meta);
    }

    private void onEdit() {
        if (workingDisplay == null || selectedLocal == null) return;
        applyToBlock();
        WorkMeta meta = selectedLocal;
        HologramScreens.openEditor(blockPos, workingDisplay, model, (tag, newModel, resources) -> {
            this.model = newModel;
            LocalGraphStore.save(new WorkPackage(meta, tag, newModel, resources));
            refreshDetail();
        });
    }

    private void onDisplay() {
        applyToBlock();
    }

    /** Edit the regular hologram's runtime placement (transform + spin) live — not persisted. */
    private void onPlacement() {
        PlacementDialog.open(root, HologramPlacements.resolve(blockPos),
                placement -> HologramPlacements.set(blockPos, placement), null);
    }

    private void applyToBlock() {
        if (workingDisplay == null || selectedLocal == null) return;
        HologramDisplays.setOverride(blockPos, workingDisplay);
        if (blockEntity != null) blockEntity.setDisplayedWorkUid(selectedLocal.uid());
        refreshLocalList(); // update the "(displayed)" marker immediately
    }

    private void onUpload() {
        if (selectedLocal == null) return;
        if (Minecraft.getInstance().player == null) return;
        String myUuid = Minecraft.getInstance().player.getUUID().toString();

        // A description is required to publish (so shared works are documented).
        String desc = String.join("\n", descriptionField.getValue());
        if (desc.isBlank()) {
            Dialog.showNotification("kilagraphdemo.ui.hologram.dlg.desc_required.title",
                    "kilagraphdemo.ui.hologram.dlg.desc_required.body", null).show(root);
            return;
        }

        // Quota pre-check (the server enforces it too): publishing creates a NEW server work unless this is an
        // update to my already-published work. Updating is always allowed; a new work needs count < limit.
        boolean mineWork = selectedLocal.authorUuid().isEmpty() || selectedLocal.authorUuid().equals(myUuid);
        boolean willCreateNew = !(mineWork && serverHasUid(selectedLocal.uid()));
        if (willCreateNew && !Kilagraphdemo.canBypassUploadLimit(Minecraft.getInstance().player)
                && myServerWorkCount() >= Kilagraphdemo.MAX_WORKS_PER_PLAYER) {
            Dialog.showNotification("kilagraphdemo.ui.hologram.dlg.limit.title",
                    I18n.get("kilagraphdemo.ui.hologram.dlg.limit.body", Kilagraphdemo.MAX_WORKS_PER_PLAYER), null).show(root);
            return;
        }

        LocalGraphStore.load(selectedLocal.uid()).ifPresent(pkg -> {
            String author = pkg.meta().authorUuid();
            boolean mine = author.isEmpty() || author.equals(myUuid);
            WorkPackage toUpload;
            if (mine) {
                // My own work (freshly authored, or my own downloaded copy): keep uid, update description.
                toUpload = pkg.withMeta(pkg.meta().withDescription(desc));
            } else {
                // Someone else's downloaded work: fork into a brand-new work owned by me (fresh uid),
                // so uploading publishes my edited version without touching the original author's work.
                toUpload = pkg.withMeta(WorkMeta.newLocal(pkg.meta().title()).withDescription(desc));
            }
            LocalGraphStore.save(toUpload);
            refreshLocalList();
            onSelectLocal(toUpload.meta());
            // Bundle any local PNG textures (rewriting their locations under this upload's uid) so the
            // shared work is self-contained. The locally-saved copy keeps its original textures/ locations.
            TextureBundler.Result result = TextureBundler.rewriteForUpload(
                    toUpload, toUpload.meta().uid(), new LocalShaderFunctions());
            if (result.tooLarge()) {
                Dialog.showNotification("kilagraphdemo.ui.hologram.dlg.too_large.title",
                        I18n.get("kilagraphdemo.ui.hologram.dlg.tex_too_large.body",
                                result.totalBytes() / (1024 * 1024) + 1,
                                TextureBundler.MAX_TOTAL_BYTES / (1024 * 1024)),
                        null).show(root);
                return;
            }
            WorkPackage uploadPkg = result.pkg() != null ? result.pkg() : toUpload;
            // Then bundle a custom OBJ model (if any), rewriting its location under this upload's uid.
            ModelBundler.Result modelResult = ModelBundler.rewriteForUpload(uploadPkg, toUpload.meta().uid());
            if (modelResult.tooLarge()) {
                Dialog.showNotification("kilagraphdemo.ui.hologram.dlg.too_large.title",
                        I18n.get("kilagraphdemo.ui.hologram.dlg.model_too_large.body",
                                modelResult.totalBytes() / (1024 * 1024) + 1,
                                ModelBundler.MAX_MODEL_BYTES / (1024 * 1024)),
                        null).show(root);
                return;
            }
            if (modelResult.pkg() != null) uploadPkg = modelResult.pkg();
            // Gate the upload: a shared work must be self-contained (all referenced shader functions
            // bundled) and must compile, or downloaders would get a broken render. (Local saves aren't gated.)
            WorkValidator.Result validation = WorkValidator.validate(uploadPkg);
            if (!validation.ok()) {
                if (!validation.missingRefs().isEmpty()) {
                    String names = String.join(", ", validation.missingRefs().stream()
                            .map(s -> {
                                IResourcePath p = IResourcePath.parse(s);
                                return p != null ? p.getResourceName() : s;
                            }).toList());
                    Dialog.showNotification("kilagraphdemo.ui.hologram.dlg.missing_refs.title",
                            I18n.get("kilagraphdemo.ui.hologram.dlg.missing_refs.body", names), null).show(root);
                } else {
                    Dialog.showNotification("kilagraphdemo.ui.hologram.dlg.compile_failed.title",
                            "kilagraphdemo.ui.hologram.dlg.compile_failed.body", null).show(root);
                }
                return;
            }
            Dialog.showNotification("kilagraphdemo.ui.hologram.dlg.uploaded.title",
                    I18n.get("kilagraphdemo.ui.hologram.dlg.uploaded.body",
                            uploadPkg.textures().size(), uploadPkg.models().size(),
                            (result.totalBytes() + modelResult.totalBytes()) / 1024), null).show(root);
            ClientWorks.upload(uploadPkg);
        });
    }

    /** Delete the local work file. (Server copies are deleted from the server entry's panel instead.) */
    private void onDeleteLocal(String uid) {
        clearDisplayIfShowing(uid);
        LocalGraphStore.delete(uid);
        if (selectedLocal != null && selectedLocal.uid().equals(uid)) {
            selectedLocal = null;
            workingDisplay = null;
        }
        refreshLocalList();
        refreshDetail();
    }

    /** If this block currently displays {@code uid}, clear the display (a deleted work must not linger). */
    private void clearDisplayIfShowing(String uid) {
        if (isCurrentlyDisplayed(uid)) {
            HologramDisplays.clearOverride(blockPos);
            if (blockEntity != null) blockEntity.setDisplayedWorkUid("");
        }
    }

    // ---- rename ------------------------------------------------------------------------------

    private void startRename(WorkMeta meta, UIElement row, Label label) {
        TextField field = new TextField();
        field.setText(meta.title());
        field.getLayout().widthPercent(100).heightPercent(100);
        label.setDisplay(false);
        row.addChild(field);
        field.focus();
        field.addEventListener(UIEvents.BLUR, e -> finishRename(meta, row, label, field));
        field.addEventListener(UIEvents.KEY_DOWN, e -> {
            if (e.keyCode == GLFW.GLFW_KEY_ENTER) {
                field.blur();
            } else if (e.keyCode == GLFW.GLFW_KEY_ESCAPE) {
                field.setText(meta.title(), false);
                field.blur();
            }
        });
    }

    private void finishRename(WorkMeta meta, UIElement row, Label label, TextField field) {
        String newTitle = field.getText().trim();
        row.removeChild(field);
        label.setDisplay(true);
        if (newTitle.isEmpty() || newTitle.equals(meta.title())) return;
        LocalGraphStore.load(meta.uid())
                .ifPresent(pkg -> LocalGraphStore.save(pkg.withMeta(pkg.meta().withTitle(newTitle))));
        if (selectedLocal != null && selectedLocal.uid().equals(meta.uid())) {
            selectedLocal = selectedLocal.withTitle(newTitle);
        }
        refreshLocalList();
        refreshDetail();
    }

    // ---- ClientWorks.Listener ----------------------------------------------------------------

    @Override
    public void onWorksUpdated(@Nullable String downloadedUid) {
        // Re-resolve the selected server entry from the fresh list so its likeCount / likedByMe (and the
        // Like button label) reflect the update — the old snapshot is stale after a like/unlike.
        if (selectedServer != null) {
            String uid = selectedServer.meta().uid();
            selectedServer = ClientWorks.serverWorks().stream()
                    .filter(en -> en.meta().uid().equals(uid)).findFirst().orElse(null);
        }
        refreshServerList();
        refreshLocalList();
        if (downloadedUid != null) {
            // A download landed — select it locally so the user can display/edit immediately.
            LocalGraphStore.list().stream().filter(m -> m.uid().equals(downloadedUid)).findFirst()
                    .ifPresent(this::onSelectLocal);
        } else {
            refreshDetail();
        }
    }

    @Override
    public void onDownloadProgress(String uid) {
        // Live progress: only repaint the detail panel if we're looking at that server work.
        if (selectedServer != null && selectedServer.meta().uid().equals(uid)) {
            refreshDetail();
        }
    }
}
