package com.lowdragmc.kilagraphdemo.client.ui;

import com.lowdragmc.kilagraphdemo.block.ServerHologramBlockEntity;
import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import com.lowdragmc.kilagraphdemo.client.editor.PlacementDialog;
import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import com.lowdragmc.kilagraphdemo.graph.ServerWorkEntry;
import com.lowdragmc.kilagraphdemo.graph.WorkMeta;
import com.lowdragmc.kilagraphdemo.network.C2SSetServerHologram;
import com.lowdragmc.kilagraphdemo.network.C2SSetServerHologramPlacement;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The <b>server hologram</b> browser — a lean cousin of {@link HologramBrowseUI} with only the server list
 * and no authoring. An op/creative player picks a published work and clicks "Set as display"; the choice is
 * sent to the server, persisted on the block, and synced to all clients (which lazily download the work when
 * they render the block). Non-privileged players can still browse but don't see the Set/Clear buttons.
 */
public class ServerHologramBrowseUI implements ClientWorks.Listener {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GlobalPos blockPos;
    @Nullable
    private final ServerHologramBlockEntity blockEntity;

    @Getter
    private final UIElement root = new UIElement();
    private final UIElement serverListContainer = new UIElement();
    private final UIElement metaLabels = new UIElement();
    private final UIElement avatarHolder = new UIElement();
    private final UIElement body = new UIElement();
    private final UIElement actions = new UIElement();
    private final AuthorAvatarView authorAvatar = new AuthorAvatarView();

    private final Map<String, UIElement> serverRows = new HashMap<>();

    @Nullable
    private ServerWorkEntry selectedServer;
    private SortMode sortMode = SortMode.LIKES_THEN_NEW;
    /** The uid currently shown by this block, tracked locally for the "(displayed)" marker + instant feedback. */
    private String displayedUid;

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

    public ServerHologramBrowseUI(GlobalPos blockPos, @Nullable ServerHologramBlockEntity blockEntity) {
        this.blockPos = blockPos;
        this.blockEntity = blockEntity;
        this.displayedUid = blockEntity == null ? "" : blockEntity.getDisplayedWorkUid();
        build();
        ClientWorks.setListener(this);
        ClientWorks.requestList();
    }

    /** Whether the local player may change this block's display (op = gamemaster level 2, or creative). */
    private boolean canEdit() {
        var player = Minecraft.getInstance().player;
        return player != null
                && (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || player.isCreative());
    }

    private void build() {
        root.addClass("panel_bg");
        root.getLayout().width(320).height(220).flexDirection(FlexDirection.ROW).gapAll(4).paddingAll(4);

        // Left column: a sort row above the server list.
        UIElement left = new UIElement();
        left.getLayout().flexDirection(FlexDirection.COLUMN).heightPercent(100).width(150).gapAll(2);
        left.addChild(sectionLabel("kilagraphdemo.ui.server_hologram.section"));
        left.addChild(sortRow());
        left.addChild(listView(serverListContainer));
        // Per-block placement (transform + spin) — op/creative only; persisted + synced on Apply.
        if (canEdit()) {
            left.addChild(new Button().setText("kilagraphdemo.ui.server_hologram.placement").setOnClick(e -> onPlacement())
                    .style(s -> s.appendTooltipsString("kilagraphdemo.ui.server_hologram.placement.tooltip")));
        }

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

        right.addChild(metaRow);
        right.addChild(body);
        right.addChild(actions);

        root.addChild(left);
        root.addChild(right);

        refreshServerList();
        refreshDetail();
    }

    private UIElement sortRow() {
        UIElement row = new UIElement();
        row.getLayout().flexDirection(FlexDirection.ROW).widthPercent(100).gapAll(1).height(12);
        row.addChild(sortButton("kilagraphdemo.ui.server_hologram.sort_liked",
                "kilagraphdemo.ui.server_hologram.sort_liked.tooltip", SortMode.MOST_LIKED));
        row.addChild(sortButton("kilagraphdemo.ui.server_hologram.sort_new",
                "kilagraphdemo.ui.server_hologram.sort_new.tooltip", SortMode.NEWEST));
        row.addChild(sortButton("kilagraphdemo.ui.server_hologram.sort_old",
                "kilagraphdemo.ui.server_hologram.sort_old.tooltip", SortMode.OLDEST));
        return row;
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

    // ---- list --------------------------------------------------------------------------------

    private void refreshServerList() {
        serverListContainer.clearAllChildren();
        serverRows.clear();
        List<ServerWorkEntry> entries = new ArrayList<>(ClientWorks.serverWorks());
        entries.removeIf(e -> e.meta().isSlideShow()); // SlideShow graph works belong to the projector browser
        entries.sort(sortMode.comparator);
        int index = 1;
        for (ServerWorkEntry entry : entries) {
            UIElement row = createServerRow(entry, index++);
            serverRows.put(entry.meta().uid(), row);
            serverListContainer.addChild(row);
        }
        highlight();
    }

    private UIElement createServerRow(ServerWorkEntry entry, int index) {
        WorkMeta m = entry.meta();
        boolean displayed = m.uid().equals(displayedUid);

        Label idx = new Label();
        idx.setText(Component.literal(index + "."));
        idx.textStyle(style -> style.textAlignVertical(Vertical.CENTER).textColor(ColorPattern.WHITE.color));
        idx.getLayout().width(12).heightPercent(100);

        String tag = "(" + (m.authorName().isEmpty() ? "?" : m.authorName()) + ")";
        Label name = new Label();
        name.setText(Component.literal(m.title() + " " + tag).append(displayed
                ? Component.translatable("kilagraphdemo.ui.server_hologram.displayed") : Component.empty()));
        name.textStyle(style -> {
            style.textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER);
            if (displayed) style.textColor(ColorPattern.GREEN.color);
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

    private void highlight() {
        String uid = selectedServer == null ? null : selectedServer.meta().uid();
        serverRows.forEach((u, row) -> setSelected(row, u.equals(uid)));
    }

    private void setSelected(UIElement row, boolean on) {
        row.style(style -> style.backgroundTexture(on ? ColorPattern.LIGHT_BLUE.rectTexture() : IGuiTexture.EMPTY));
    }

    // ---- detail ------------------------------------------------------------------------------

    private void onSelectServer(ServerWorkEntry entry) {
        selectedServer = entry;
        highlight();
        refreshDetail();
    }

    private void refreshDetail() {
        metaLabels.clearAllChildren();
        avatarHolder.clearAllChildren();
        body.clearAllChildren();
        actions.clearAllChildren();
        if (selectedServer == null) {
            metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.server_hologram.select_work"), ColorPattern.WHITE.color));
            return;
        }
        buildServerDetail(selectedServer);
    }

    private Label metaLabel(Component text, int color) {
        Label l = new Label();
        l.setText(text);
        l.textStyle(style -> style.textColor(color).textShadow(false)
                .textWrap(TextWrap.HOVER_ROLL).textAlignVertical(Vertical.CENTER));
        l.setOverflowVisible(false);
        l.getLayout().widthPercent(100).height(9);
        return l;
    }

    private void buildServerDetail(ServerWorkEntry entry) {
        WorkMeta m = entry.meta();
        boolean displayed = m.uid().equals(displayedUid);

        metaLabels.addChild(metaLabel(Component.literal(m.title()), ColorPattern.YELLOW.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.server_hologram.meta_by",
                m.authorName().isEmpty() ? "?" : m.authorName()), ColorPattern.CYAN.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.server_hologram.meta_first", date(m.firstUploadTime())), ColorPattern.GRAY.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.server_hologram.meta_updated", date(m.lastUpdateTime())), ColorPattern.GRAY.color));
        metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.server_hologram.meta_likes", entry.likeCount()), ColorPattern.PINK.color));
        if (displayed) metaLabels.addChild(metaLabel(Component.translatable("kilagraphdemo.ui.server_hologram.currently_displayed"), ColorPattern.GREEN.color));

        if (!m.authorUuid().isEmpty()) {
            try {
                authorAvatar.setAuthor(UUID.fromString(m.authorUuid()), m.authorName());
                avatarHolder.addChild(authorAvatar);
            } catch (IllegalArgumentException ignored) {
                // malformed uuid — skip the avatar
            }
        }

        body.addChild(descriptionScroller(m.description()));

        // Like is available to anyone; Set/Clear only to ops/creative.
        actions.addChild(new Button().setText(entry.likedByMe() ? "kilagraphdemo.ui.common.unlike" : "kilagraphdemo.ui.common.like")
                .setOnClick(e -> ClientWorks.setLike(m.uid(), !entry.likedByMe()))
                .style(s -> s.appendTooltipsString("kilagraphdemo.ui.common.like.tooltip")));
        if (canEdit()) {
            if (!displayed) {
                actions.addChild(new Button().setText("kilagraphdemo.ui.server_hologram.set_display").setOnClick(e -> setDisplay(m.uid()))
                        .style(s -> s.appendTooltipsString("kilagraphdemo.ui.server_hologram.set_display.tooltip")));
            }
            if (!displayedUid.isEmpty()) {
                actions.addChild(new Button().setText("kilagraphdemo.ui.server_hologram.clear_display").setOnClick(e -> setDisplay(""))
                        .style(s -> s.appendTooltipsString("kilagraphdemo.ui.server_hologram.clear_display.tooltip")));
            }
        }
    }

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

    /**
     * Edit the server hologram's placement. No live preview (the block renders from its synced placement);
     * the <b>Apply</b> button sends one packet → the server persists + syncs it back.
     */
    private void onPlacement() {
        HologramPlacement initial = blockEntity == null ? HologramPlacement.DEFAULT : blockEntity.getPlacement();
        PlacementDialog.open(root, initial, p -> {},
                p -> ClientPacketDistributor.sendToServer(new C2SSetServerHologramPlacement(blockPos.pos(), p)));
    }

    /** Send the new display selection to the server and optimistically update the local marker. */
    private void setDisplay(String uid) {
        ClientPacketDistributor.sendToServer(new C2SSetServerHologram(blockPos.pos(), uid));
        displayedUid = uid;
        refreshServerList();
        refreshDetail();
    }

    private static String date(long ms) {
        return ms <= 0 ? "—" : Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(FMT);
    }

    // ---- ClientWorks.Listener ----------------------------------------------------------------

    @Override
    public void onWorksUpdated(@Nullable String downloadedUid) {
        // Re-resolve the selected entry from the fresh list so likeCount / likedByMe reflect updates.
        if (selectedServer != null) {
            String uid = selectedServer.meta().uid();
            selectedServer = ClientWorks.serverWorks().stream()
                    .filter(en -> en.meta().uid().equals(uid)).findFirst().orElse(null);
        }
        refreshServerList();
        refreshDetail();
    }
}
