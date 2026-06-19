package com.lowdragmc.kilagraphdemo.client.drone;

import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.farm.Stage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders a drone station's live game board: the pumpkins on its field (colored cubes scaled by stage,
 * a single oversized cube for a merged N×N block) and the drone hovering over its current cell with a
 * smooth eased motion. Reads the server-synced snapshot off the {@link DroneStationBlockEntity}.
 *
 * <p>First-cut visuals use solid colored cubes ({@link RenderTypes#debugQuads()}); textured pumpkin
 * models are a later polish. The station block sits at local origin; the field's soil top is local
 * {@code y=0} (soil is the block below), so pumpkins grow upward from {@code y=0}.</p>
 */
public class DroneStationRenderer implements BlockEntityRenderer<DroneStationBlockEntity, DroneStationRenderState> {

    /** Drone hover height above the soil, in block units (above the station's top). */
    private static final float DRONE_HOVER = 1.4f;
    private static final float DRONE_HALF = 0.22f;
    private static final float EASE = 0.18f;

    /** Per-station eased drone position (local field coords), so motion lerps between synced cells. */
    private static final Map<BlockPos, float[]> EASED = new HashMap<>();

    public DroneStationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public DroneStationRenderState createRenderState() {
        return new DroneStationRenderState();
    }

    @Override
    public void extractRenderState(DroneStationBlockEntity be, DroneStationRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.pos = be.getBlockPos();
        state.fieldWidth = be.getFieldWidth();
        state.fieldHeight = be.getFieldHeight();
        state.fieldOffX = be.getFieldOffX();
        state.fieldOffZ = be.getFieldOffZ();
        state.cells = be.getCells();
        state.droneX = be.getDroneX();
        state.droneZ = be.getDroneZ();
        // Key visibility off the field geometry (a plain synced int), not the cells array: an array
        // shrinking back to empty on reset is not a reliable @DescSynced delta, so the drone/board could
        // otherwise linger after a run ends.
        state.active = be.getFieldWidth() > 0;
        Level level = be.getLevel();
        state.time = level == null ? 0f : (level.getGameTime() + partialTicks);
    }

    @Override
    public void submit(DroneStationRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buf) -> {
            drawPumpkins(pose, buf, state);
            drawDrone(pose, buf, state);
        });
    }

    private void drawPumpkins(PoseStack.Pose pose, VertexConsumer buf, DroneStationRenderState state) {
        int w = state.fieldWidth;
        int h = state.fieldHeight;
        if (w <= 0 || h <= 0 || state.cells.length < w * h) return;
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int cell = state.cells[z * w + x];
                Stage stage = Stage.values()[cell & 0xFF];
                int merge = (cell >> 8) & 0xFF;
                float bx = state.fieldOffX + x;
                float bz = state.fieldOffZ + z;
                switch (stage) {
                    case GROWING -> cube(pose, buf, bx + 0.25f, 0f, bz + 0.25f, bx + 0.75f, 0.5f, bz + 0.75f,
                            0x4C, 0xAF, 0x50);                       // green sprout
                    case RIPE -> {
                        int n = Math.max(1, merge);
                        float pad = n == 1 ? 0.1f : 0.05f;
                        cube(pose, buf, bx + pad, 0f, bz + pad, bx + n - pad, 0.55f * n, bz + n - pad,
                                0xE0, 0x80, 0x20);                   // pumpkin orange (big for merged)
                    }
                    case ROTTEN -> cube(pose, buf, bx + 0.15f, 0f, bz + 0.15f, bx + 0.85f, 0.55f, bz + 0.85f,
                            0x5A, 0x40, 0x30);                       // dark, rotten
                    default -> { }                                   // EMPTY / MERGED_MEMBER: nothing
                }
            }
        }
    }

    private void drawDrone(PoseStack.Pose pose, VertexConsumer buf, DroneStationRenderState state) {
        float targetX, targetZ;
        if (state.active) {
            targetX = state.fieldOffX + state.droneX + 0.5f;
            targetZ = state.fieldOffZ + state.droneZ + 0.5f;
        } else {
            targetX = 0.5f; // parked above the station block when idle
            targetZ = 0.5f;
        }
        float[] cur = EASED.computeIfAbsent(state.pos, k -> new float[]{targetX, targetZ});
        cur[0] += (targetX - cur[0]) * EASE;
        cur[1] += (targetZ - cur[1]) * EASE;

        float bob = (float) Math.sin(state.time * 0.15f) * 0.06f;
        float cx = cur[0];
        float cz = cur[1];
        float y = DRONE_HOVER + bob;
        cube(pose, buf, cx - DRONE_HALF, y - DRONE_HALF, cz - DRONE_HALF,
                cx + DRONE_HALF, y + DRONE_HALF, cz + DRONE_HALF, 0x30, 0x70, 0xE0); // blue drone body
    }

    /** Emit an axis-aligned box as 6 color quads (both windings, so it's visible regardless of culling). */
    private static void cube(PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             int r, int g, int b) {
        // bottom (y0) and top (y1)
        quad(pose, buf, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b);
        quad(pose, buf, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b);
        // north (z0) and south (z1)
        quad(pose, buf, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b);
        quad(pose, buf, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b);
        // west (x0) and east (x1)
        quad(pose, buf, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b);
        quad(pose, buf, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b);
    }

    /** One color quad, emitted in both windings so back-face culling can't hide it. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buf,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int r, int g, int b) {
        buf.addVertex(pose, ax, ay, az).setColor(r, g, b, 255);
        buf.addVertex(pose, bx, by, bz).setColor(r, g, b, 255);
        buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, 255);
        buf.addVertex(pose, dx, dy, dz).setColor(r, g, b, 255);
        buf.addVertex(pose, dx, dy, dz).setColor(r, g, b, 255);
        buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, 255);
        buf.addVertex(pose, bx, by, bz).setColor(r, g, b, 255);
        buf.addVertex(pose, ax, ay, az).setColor(r, g, b, 255);
    }

    @Override
    public AABB getRenderBoundingBox(DroneStationBlockEntity be) {
        BlockPos p = be.getBlockPos();
        int w = be.getFieldWidth();
        int h = be.getFieldHeight();
        AABB block = new AABB(p);
        if (w <= 0 || h <= 0) return block.inflate(1);
        double minX = p.getX() + be.getFieldOffX();
        double minZ = p.getZ() + be.getFieldOffZ();
        return new AABB(minX, p.getY() - 1, minZ, minX + w, p.getY() + 3, minZ + h).minmax(block);
    }
}
