package com.lowdragmc.kilagraphdemo.client;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewRenderer;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraphdemo.client.render.HologramDisplay;
import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import com.lowdragmc.kilagraphdemo.graph.ModelTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared geometry/submission for the hologram projection — the cyan light "cone" and the graph-shaded model
 * floating above it. Both {@link HologramRenderer} (client-chosen display) and
 * {@link ServerHologramRenderer} (server-synced display) draw the same projection, so the geometry lives
 * here once. The cone is built in the floor frame and rotated to the block's {@code FACING}.
 */
public final class HologramProjection {

    /** Slow spin (degrees/tick) of the projected model. */
    public static final float SPIN_DEGREES_PER_TICK = 2f;
    /** Height of the projected content above the block centre, along the facing axis (block units). */
    public static final float MODEL_OFFSET = 0.85f;

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

    private static final float MODEL_SCALE = 0.45f;   // unit model -> ~0.45 block

    private HologramProjection() {
    }

    /** Rotation aligning local +Y with the mounting face (the cone/model are symmetric, so roll is irrelevant). */
    public static Quaternionf facingRotation(Direction facing) {
        Vector3f dir = facing.step();
        return new Quaternionf().rotationTo(0f, 1f, 0f, dir.x(), dir.y(), dir.z());
    }

    /** Draw the cyan projector cone, aligned to the mounting face. */
    public static void submitCone(PoseStack poseStack, SubmitNodeCollector collector, Quaternionf facingRotation) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(facingRotation);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), HologramProjection::drawFrustum);
        poseStack.popPose();
    }

    /**
     * Draw the graph-shaded model floating above the projector, with the per-block {@link HologramPlacement}
     * (translate / rotate / scale) applied on top of the base placement and an auto-spin of {@code spinDegrees}.
     * A {@link HologramPlacement#DEFAULT} placement reproduces the historical fixed look.
     */
    public static void submitModel(PoseStack poseStack, SubmitNodeCollector collector, Quaternionf facingRotation,
                                   float spinDegrees, HologramPlacement placement, HologramDisplay display,
                                   RenderTypeGraphMaterial material) {
        ModelTransform t = placement.transform();
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(facingRotation);                 // orient with the mounting face
        poseStack.translate(0f, MODEL_OFFSET, 0f);         // float above the projector along the facing axis
        // Per-block placement: offset, then Euler rotation (X→Y→Z), then scale.
        poseStack.translate(t.offsetX(), t.offsetY(), t.offsetZ());
        poseStack.mulPose(Axis.XP.rotationDegrees(t.rotX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(t.rotY()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(t.rotZ()));
        poseStack.scale(MODEL_SCALE * t.scaleX(), MODEL_SCALE * t.scaleY(), MODEL_SCALE * t.scaleZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees)); // auto-spin around the facing axis (0 = none)

        RenderType renderType = material.renderType();
        RenderTypeGraph graph = display.graph();
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                PreviewRenderer.render(display.content(), pose, buffer,
                        renderType.format(), graph.getSettings().vertexFormatMode()));
        poseStack.popPose();
    }

    /**
     * Conservative cull half-extent (block units) for a projection: the work's render radius scaled by the
     * placement's max scale, plus the placement offset reach and the base model offset. The renderer inflates
     * the block's AABB by this so an off-screen projector whose projection is in view isn't culled.
     */
    public static float cullHalfExtent(HologramPlacement placement, float renderRadius) {
        ModelTransform t = placement.transform();
        return renderRadius * Math.max(1f, t.maxScale()) + t.offsetLength() + MODEL_OFFSET;
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
