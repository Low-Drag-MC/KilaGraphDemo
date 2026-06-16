package com.lowdragmc.kilagraphdemo.client;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewRenderer;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraphdemo.block.HologramBlock;
import com.lowdragmc.kilagraphdemo.block.HologramBlockEntity;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplays;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.math.Axis;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the hologram's projected light: an upward-widening frustum (truncated pyramid) above the
 * projector, glowing cyan and fading from opaque at its base to fully transparent at its top.
 *
 * <p>Uses {@link RenderTypes#lightning()} (POSITION_COLOR, additive translucent, untextured, no cull)
 * for a self-illuminated glow. The frustum is built in the floor frame and rotated so its axis aligns
 * with the block's {@code FACING}, making the light follow whichever surface the block is mounted on.
 */
public class HologramRenderer implements BlockEntityRenderer<HologramBlockEntity, HologramRenderState> {
    // Frustum geometry, in full-block units (0..1).
    private static final float BOTTOM_Y = 5f / 16f;   // light starts 5/16 above the block floor
    private static final float TOP_Y = 10f / 16f;     // ...and is 5/16 tall
    private static final float BOTTOM_HALF = 0.25f;   // bottom square: side 0.5
    private static final float TOP_HALF = 0.5f;       // top square: side 1.0

    // Cyan light colour.
    private static final int R = 0;
    private static final int G = 255;
    private static final int B = 255;
    private static final int BOTTOM_ALPHA = 255;
    private static final int TOP_ALPHA = 0;

    // Projected model placement (in the facing-aligned local frame, block-units).
    private static final float MODEL_OFFSET = 0.85f;   // height above block centre along the facing axis
    private static final float MODEL_SCALE = 0.45f;    // unit model -> ~0.45 block
    private static final float SPIN_DEGREES_PER_TICK = 2f;

    public HologramRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public HologramRenderState createRenderState() {
        return new HologramRenderState();
    }

    @Override
    public void extractRenderState(HologramBlockEntity blockEntity, HologramRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(HologramBlock.FACING);
        Level level = blockEntity.getLevel();
        if (level != null) {
            state.posKey = GlobalPos.of(level.dimension(), blockEntity.getBlockPos());
            state.spinDegrees = ((level.getGameTime() + partialTicks) * SPIN_DEGREES_PER_TICK) % 360f;
        } else {
            state.posKey = null;
        }
    }

    @Override
    public void submit(HologramRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        // 1) The projector "light beam": the cyan cone, aligned to the mounting face.
        poseStack.pushPose();
        Quaternionf facingRotation = facingRotation(state);
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(facingRotation);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), HologramRenderer::drawFrustum);
        poseStack.popPose();

        // 2) The projected hologram: a model shaded by the displayed RenderTypeGraph, floating above
        //    the projector. Skipped (cone only) while the graph fails to compile.
        HologramDisplay display = state.posKey != null
                ? HologramDisplays.resolve(state.posKey)
                : HologramDisplays.getDefault();
        RenderTypeGraphMaterial material = display.updateMaterial();
        if (material != null) {
            submitModel(state, poseStack, collector, facingRotation, display, material);
        }
    }

    private static void submitModel(HologramRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                    Quaternionf facingRotation, HologramDisplay display,
                                    RenderTypeGraphMaterial material) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(facingRotation);                 // orient with the mounting face
        poseStack.translate(0f, MODEL_OFFSET, 0f);         // float above the projector along the facing axis
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spinDegrees)); // slow spin around the facing axis

        RenderType renderType = material.renderType();
        RenderTypeGraph graph = display.graph();
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                PreviewRenderer.render(display.content(), pose, buffer,
                        renderType.format(), graph.getSettings().vertexFormatMode()));
        poseStack.popPose();
    }

    /** Rotation aligning local +Y with the mounting face (the cone/model are symmetric, so roll is irrelevant). */
    private static Quaternionf facingRotation(HologramRenderState state) {
        Vector3f dir = state.facing.step();
        return new Quaternionf().rotationTo(0f, 1f, 0f, dir.x(), dir.y(), dir.z());
    }

    private static void drawFrustum(PoseStack.Pose pose, VertexConsumer buf) {
        float bMin = 0.5f - BOTTOM_HALF, bMax = 0.5f + BOTTOM_HALF; // 0.25 .. 0.75
        float tMin = 0.5f - TOP_HALF, tMax = 0.5f + TOP_HALF;       // 0.0  .. 1.0

        // North (-Z) face
        sideQuad(pose, buf, bMin, bMin, bMax, bMin, tMax, tMin, tMin, tMin);
        // South (+Z) face
        sideQuad(pose, buf, bMax, bMax, bMin, bMax, tMin, tMax, tMax, tMax);
        // West (-X) face
        sideQuad(pose, buf, bMin, bMax, bMin, bMin, tMin, tMax, tMin, tMin);
        // East (+X) face
        sideQuad(pose, buf, bMax, bMin, bMax, bMax, tMax, tMin, tMax, tMax);
    }

    /**
     * Emits one trapezoidal side quad: the two bottom vertices (opaque) followed by the two top
     * vertices (transparent). Parameters are the x/z coordinates of bottom-left, bottom-right,
     * top-right, top-left.
     */
    private static void sideQuad(PoseStack.Pose pose, VertexConsumer buf,
                                 float bx0, float bz0, float bx1, float bz1,
                                 float tx1, float tz1, float tx0, float tz0) {
        // Front winding.
        vertex(pose, buf, bx0, BOTTOM_Y, bz0, BOTTOM_ALPHA);
        vertex(pose, buf, bx1, BOTTOM_Y, bz1, BOTTOM_ALPHA);
        vertex(pose, buf, tx1, TOP_Y, tz1, TOP_ALPHA);
        vertex(pose, buf, tx0, TOP_Y, tz0, TOP_ALPHA);
        // Back winding, so the cone is visible from both sides (lightning() culls back faces).
        vertex(pose, buf, tx0, TOP_Y, tz0, TOP_ALPHA);
        vertex(pose, buf, tx1, TOP_Y, tz1, TOP_ALPHA);
        vertex(pose, buf, bx1, BOTTOM_Y, bz1, BOTTOM_ALPHA);
        vertex(pose, buf, bx0, BOTTOM_Y, bz0, BOTTOM_ALPHA);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buf, float x, float y, float z, int alpha) {
        buf.addVertex(pose, x, y, z).setColor(R, G, B, alpha);
    }
}
