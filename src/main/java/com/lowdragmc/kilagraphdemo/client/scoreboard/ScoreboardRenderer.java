package com.lowdragmc.kilagraphdemo.client.scoreboard;

import com.lowdragmc.kilagraphdemo.block.AbstractScoreboardBlockEntity;
import com.lowdragmc.kilagraphdemo.block.AbstractScoreboardBlockEntity.Row;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a scoreboard block's leaderboard as a vertical billboard standing above the block and facing the way
 * the block was placed: a dark, semi-transparent panel ({@link RenderTypes#textBackground()}) with a centred
 * title and up to {@link AbstractScoreboardBlockEntity#MAX_ROWS} rows of {@code rank. name … value}. The top
 * three ranks are tinted gold / silver / bronze; the rest are light grey. All text is drawn
 * {@link Font.DisplayMode#SEE_THROUGH} so it never gets occluded by the panel.
 */
public class ScoreboardRenderer implements BlockEntityRenderer<AbstractScoreboardBlockEntity, ScoreboardRenderState> {

    private static final int LIGHT = LightCoordsUtil.FULL_BRIGHT;

    // Panel geometry (block units), measured in the block-top-centred local frame.
    private static final float PANEL_W = 9f;
    private static final float PANEL_H = 9f;
    private static final float BOTTOM = 0.5f;            // panel bottom, above the block top
    private static final float TOP = BOTTOM + PANEL_H;
    private static final float MARGIN = 0.4f;            // inner padding for text
    private static final float TEXT_SCALE = 0.05f;       // glyph px -> block units
    private static final float FRONT_Z = 0.02f;          // nudge text in front of the panel

    private static final int BG_ARGB = 0xB0121214;       // gray-black, ~69% opaque
    private static final int TITLE_COLOR = 0xFFFFE066;
    private static final int SUB_COLOR = 0xFFA0A0A0;
    private static final int[] RANK_COLOR = {0xFFFFD700, 0xFFC0C0C0, 0xFFCD7F32}; // gold / silver / bronze
    private static final int DEFAULT_COLOR = 0xFFE0E0E0;

    public ScoreboardRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public ScoreboardRenderState createRenderState() {
        return new ScoreboardRenderState();
    }

    @Override
    public void extractRenderState(AbstractScoreboardBlockEntity be, ScoreboardRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.pos = be.getBlockPos();
        state.facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        CompoundTag board = be.getBoard();
        state.title = board.getStringOr("title", "");
        List<Row> rows = new ArrayList<>();
        for (Tag t : board.getListOrEmpty("rows")) {
            if (t instanceof CompoundTag rt) {
                rows.add(new Row(rt.getStringOr("name", ""), rt.getStringOr("sub", ""), rt.getIntOr("value", 0)));
            }
        }
        state.rows = rows;
        Level level = be.getLevel();
        state.time = level == null ? 0f : (level.getGameTime() + partialTicks);
    }

    @Override
    public void submit(ScoreboardRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.0, 0.5);                                    // block top centre
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));    // panel front faces `facing`

        // Dark translucent backing panel. Depth-writing (textBackground) so the world — clouds, terrain —
        // occludes it correctly. The text is drawn NORMAL (depth-tested) and nudged FRONT_Z toward the
        // viewer, so depth separation keeps the glyphs on top of the panel regardless of pass order — that
        // is what stops the panel tinting them grey. Drawn both sides so it reads from behind too.
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(),
                (pose, buf) -> drawPanel(pose, buf));

        // Text overlay.
        poseStack.pushPose();
        float textAreaW = PANEL_W - 2 * MARGIN;
        poseStack.translate(-textAreaW / 2f, TOP - MARGIN, FRONT_Z);           // top-left of the text area
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);                  // glyph y-down -> world y-up
        drawText(state, poseStack, collector, textAreaW / TEXT_SCALE);
        poseStack.popPose();

        poseStack.popPose();
    }

    /** One dark quad spanning the panel, emitted with both windings so it shows from either side. */
    private static void drawPanel(PoseStack.Pose pose, VertexConsumer buf) {
        int a = (BG_ARGB >>> 24) & 0xFF, r = (BG_ARGB >> 16) & 0xFF, g = (BG_ARGB >> 8) & 0xFF, b = BG_ARGB & 0xFF;
        float x0 = -PANEL_W / 2f, x1 = PANEL_W / 2f;
        vtx(pose, buf, x0, BOTTOM, r, g, b, a);
        vtx(pose, buf, x1, BOTTOM, r, g, b, a);
        vtx(pose, buf, x1, TOP, r, g, b, a);
        vtx(pose, buf, x0, TOP, r, g, b, a);
        // reverse winding (back face)
        vtx(pose, buf, x0, TOP, r, g, b, a);
        vtx(pose, buf, x1, TOP, r, g, b, a);
        vtx(pose, buf, x1, BOTTOM, r, g, b, a);
        vtx(pose, buf, x0, BOTTOM, r, g, b, a);
    }

    private static void vtx(PoseStack.Pose pose, VertexConsumer buf, float x, float y, int r, int g, int b, int a) {
        buf.addVertex(pose, x, y, 0f).setColor(r, g, b, a).setLight(LIGHT);
    }

    private void drawText(ScoreboardRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                          float areaWGlyph) {
        Font font = Minecraft.getInstance().font;
        float rowStep = font.lineHeight + 4f;
        float gy = 0f;

        // Title — centred.
        if (!state.title.isEmpty()) {
            float tx = Math.max(0f, (areaWGlyph - font.width(state.title)) / 2f);
            text(poseStack, collector, tx, gy, state.title, TITLE_COLOR);
        }
        gy += rowStep * 1.7f;

        // Rows — left "rank. name (author)", right value.
        for (int i = 0; i < state.rows.size(); i++) {
            Row row = state.rows.get(i);
            int color = i < RANK_COLOR.length ? RANK_COLOR[i] : DEFAULT_COLOR;

            String valueStr = Integer.toString(row.value());
            float valueW = font.width(valueStr);
            text(poseStack, collector, areaWGlyph - valueW, gy, valueStr, color);

            String left = (i + 1) + ". " + row.name();
            if (row.sub() != null && !row.sub().isEmpty()) {
                left += " (" + row.sub() + ")";
            }
            int maxLeft = (int) (areaWGlyph - valueW - 6f); // keep a gap before the value
            left = font.plainSubstrByWidth(left, maxLeft);
            text(poseStack, collector, 0f, gy, left, color);

            gy += rowStep;
        }
    }

    private void text(PoseStack poseStack, SubmitNodeCollector collector, float x, float y, String s, int color) {
        collector.submitText(poseStack, x, y, Component.literal(s).getVisualOrderText(),
                true, Font.DisplayMode.NORMAL, LIGHT, color, 0, 0);
    }

    @Override
    public AABB getRenderBoundingBox(AbstractScoreboardBlockEntity be) {
        BlockPos p = be.getBlockPos();
        // Cover the full billboard regardless of facing so it isn't frustum-culled.
        return new AABB(p.getX() - 5, p.getY(), p.getZ() - 5, p.getX() + 6, p.getY() + 11, p.getZ() + 6);
    }
}
