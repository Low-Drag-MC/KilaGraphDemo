package com.lowdragmc.kilagraphdemo.client.drone;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.client.KilagraphdemoClient;
import com.lowdragmc.kilagraphdemo.farm.Stage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders a drone station's live game board: textured pumpkins on its field (cubes that sit on the soil
 * with a small gap from their neighbours; a merged N×N block renders as one larger cube whose top texture
 * stretches over the footprint; rotten pumpkins are tinted green) and a quad-rotor drone that hovers over
 * its current cell — bobbing, spinning its rotors, and leaning into its movement.
 *
 * <p>Pumpkins use the {@code pumpkin_side}/{@code pumpkin_top} textures via {@link RenderTypes#entityCutout};
 * the drone is solid-coloured geometry ({@link RenderTypes#debugQuads()}). The station block sits at local
 * origin, soil top is local {@code y=0}, so pumpkins grow upward from {@code y=0}.</p>
 */
public class DroneStationRenderer implements BlockEntityRenderer<DroneStationBlockEntity, DroneStationRenderState> {

    private static final Identifier PUMPKIN_SIDE =
            Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "textures/block/pumpkin_side.png");
    private static final Identifier PUMPKIN_TOP =
            Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "textures/block/pumpkin_top.png");

    private static final int LIGHT = LightCoordsUtil.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    private static final float DRONE_HOVER = 1.4f;
    private static final float EASE = 0.2f;
    /** Degrees of lean per block/tick of horizontal speed, capped so a fast hop doesn't flip the drone. */
    private static final float TILT_PER_SPEED = 320f;
    private static final float TILT_MAX = 32f;

    /** Per-station eased drone state {@code {curX, curZ, velX, velZ}} for smooth motion + lean. */
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
        // Visibility keys off the field geometry (a plain synced int), not the cells array: an array
        // shrinking back to empty on reset is not a reliable @DescSynced delta.
        state.active = be.getFieldWidth() > 0;
        Level level = be.getLevel();
        state.time = level == null ? 0f : (level.getGameTime() + partialTicks);
    }

    @Override
    public void submit(DroneStationRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        // Pumpkins: two textured passes (side faces, then top/bottom faces) so each pass binds one texture.
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(PUMPKIN_SIDE),
                (pose, buf) -> forEachPumpkin(state, (x0, z0, size, height, inset, r, g, b) ->
                        emitSides(pose, buf, x0, z0, size, height, inset, r, g, b)));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(PUMPKIN_TOP),
                (pose, buf) -> forEachPumpkin(state, (x0, z0, size, height, inset, r, g, b) ->
                        emitTopBottom(pose, buf, x0, z0, size, height, inset, r, g, b)));

        submitDrone(state, poseStack, collector);
    }

    // ---- pumpkins -----------------------------------------------------------------------------

    @FunctionalInterface
    private interface PumpkinSink {
        /**
         * {@code (x0,z0)} = field-local NW corner of the footprint; {@code size} cells wide/deep;
         * {@code inset} = gap from each cell edge (larger = smaller cube).
         */
        void accept(float x0, float z0, int size, float height, float inset, int r, int g, int b);
    }

    /** Walk the field grid and feed each visible pumpkin's footprint + size + colour to {@code sink}. */
    private void forEachPumpkin(DroneStationRenderState state, PumpkinSink sink) {
        int w = state.fieldWidth;
        int h = state.fieldHeight;
        if (w <= 0 || h <= 0 || state.cells.length < w * h) return;
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int cell = state.cells[z * w + x];
                Stage stage = Stage.values()[cell & 0xFF];
                int merge = Math.max(1, (cell >> 8) & 0xFF);
                float bx = state.fieldOffX + x;
                float bz = state.fieldOffZ + z;
                // Each stage has its own footprint (inset) and height, all sitting flush on y=0.
                switch (stage) {
                    case GROWING -> sink.accept(bx, bz, 1, 0.32f, 0.30f, 0x6F, 0xB0, 0x4A);     // small green sprout
                    case RIPE -> sink.accept(bx, bz, merge, 0.55f * merge, 0.09f, 0xFF, 0xFF, 0xFF); // full, textured
                    case ROTTEN -> sink.accept(bx, bz, 1, 0.45f, 0.18f, 0x5C, 0x8A, 0x32);      // medium, green-tinted
                    default -> { }                                                               // EMPTY / MERGED_MEMBER
                }
            }
        }
    }

    /** Emit the 4 side faces of a pumpkin cube (footprint {@code size}², inset, sitting on {@code y=0}). */
    private void emitSides(PoseStack.Pose pose, VertexConsumer buf, float x0, float z0, int size, float height,
                           float inset, int r, int g, int b) {
        float ax = x0 + inset, az = z0 + inset;
        float bx = x0 + size - inset, bz = z0 + size - inset;
        float y1 = height;
        texQuad(pose, buf, ax, 0, az, ax, y1, az, bx, y1, az, bx, 0, az, r, g, b, 0, 0, -1); // north
        texQuad(pose, buf, bx, 0, bz, bx, y1, bz, ax, y1, bz, ax, 0, bz, r, g, b, 0, 0, 1);  // south
        texQuad(pose, buf, ax, 0, bz, ax, y1, bz, ax, y1, az, ax, 0, az, r, g, b, -1, 0, 0); // west
        texQuad(pose, buf, bx, 0, az, bx, y1, az, bx, y1, bz, bx, 0, bz, r, g, b, 1, 0, 0);  // east
    }

    /** Emit top (and bottom) faces; UV spans 0..1 over the whole footprint so a merged top stretches. */
    private void emitTopBottom(PoseStack.Pose pose, VertexConsumer buf, float x0, float z0, int size, float height,
                               float inset, int r, int g, int b) {
        float ax = x0 + inset, az = z0 + inset;
        float bx = x0 + size - inset, bz = z0 + size - inset;
        float y1 = height;
        texQuad(pose, buf, ax, y1, az, ax, y1, bz, bx, y1, bz, bx, y1, az, r, g, b, 0, 1, 0); // top
        texQuad(pose, buf, ax, 0, bz, ax, 0, az, bx, 0, az, bx, 0, bz, r, g, b, 0, -1, 0);    // bottom
    }

    /** One textured quad (full 0..1 UV) for the entity-cutout passes. */
    private static void texQuad(PoseStack.Pose pose, VertexConsumer buf,
                                float ax, float ay, float az, float bx, float by, float bz,
                                float cx, float cy, float cz, float dx, float dy, float dz,
                                int r, int g, int b, float nx, float ny, float nz) {
        buf.addVertex(pose, ax, ay, az).setColor(r, g, b, 255).setUv(0, 1).setOverlay(OVERLAY).setLight(LIGHT).setNormal(nx, ny, nz);
        buf.addVertex(pose, bx, by, bz).setColor(r, g, b, 255).setUv(0, 0).setOverlay(OVERLAY).setLight(LIGHT).setNormal(nx, ny, nz);
        buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, 255).setUv(1, 0).setOverlay(OVERLAY).setLight(LIGHT).setNormal(nx, ny, nz);
        buf.addVertex(pose, dx, dy, dz).setColor(r, g, b, 255).setUv(1, 1).setOverlay(OVERLAY).setLight(LIGHT).setNormal(nx, ny, nz);
    }

    // ---- drone --------------------------------------------------------------------------------

    private void submitDrone(DroneStationRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        BlockStateModelPart part = Minecraft.getInstance().getModelManager()
                .getStandaloneModel(KilagraphdemoClient.DRONE_MODEL);
        if (part == null) return; // model not baked yet (e.g. mid resource reload)

        float targetX = state.active ? state.fieldOffX + state.droneX + 0.5f : 0.5f;
        float targetZ = state.active ? state.fieldOffZ + state.droneZ + 0.5f : 0.5f;
        float[] e = EASED.computeIfAbsent(state.pos, k -> new float[]{targetX, targetZ, 0, 0});
        float prevX = e[0], prevZ = e[1];
        e[0] += (targetX - e[0]) * EASE;
        e[1] += (targetZ - e[1]) * EASE;
        e[2] = e[0] - prevX; // velocity this frame
        e[3] = e[1] - prevZ;

        float bob = Mth.sin(state.time * 0.15f) * 0.06f;
        float spin = state.time * 1.4f; // rotor angle
        float tiltX = Mth.clamp(e[3] * TILT_PER_SPEED, -TILT_MAX, TILT_MAX);
        float tiltZ = Mth.clamp(-e[2] * TILT_PER_SPEED, -TILT_MAX, TILT_MAX);

        poseStack.pushPose();
        poseStack.translate(e[0], DRONE_HOVER + bob, e[1]);
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        poseStack.scale(1.3f, 1.3f, 1.3f);
        // drone.json is a 0..1 block model centred near (0.5, ~0.47, 0.5); recentre it on the cell so the
        // pivot above sits at the model's middle (independent of the scale applied just before).
        poseStack.translate(-0.5f, -0.47f, -0.5f);
        collector.submitMultiLayerBlockModel(poseStack, java.util.List.of(part), false,
                BlockModelRenderState.EMPTY_TINTS, LIGHT, OVERLAY, 0);
        // The model keeps the hollow rotor mounts; fill each with our spinning blades (same 0..1 block
        // space as the model, so the model-space rotor centres below line up with the mounts).
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                (pose, buf) -> drawRotors(pose, buf, spin));
        poseStack.popPose();
    }

    // ---- rotor blades -------------------------------------------------------------------------

    /** Rotor-mount centres in the model's 0..16 block space (the 4 hollow corner ducts), as 0..1 coords. */
    private static final float ROTOR_Y = 7.5f / 16f;
    private static final float[] ROTOR_X = {4.5f / 16f, 11.5f / 16f, 4.5f / 16f, 11.5f / 16f};
    private static final float[] ROTOR_Z = {4.5f / 16f, 4.5f / 16f, 11.5f / 16f, 11.5f / 16f};
    /** Blade half-length (radius) + half-width; the radius stays inside each mount's 3×3 inner hole. */
    private static final float BLADE_LEN = 1.4f / 16f;
    private static final float BLADE_WID = 0.4f / 16f;

    /** Draw all 4 corner rotors' spinning blades (2 perpendicular blades each → a "+" prop). */
    private void drawRotors(PoseStack.Pose pose, VertexConsumer buf, float spin) {
        for (int i = 0; i < ROTOR_X.length; i++) {
            blade(pose, buf, ROTOR_X[i], ROTOR_Y, ROTOR_Z[i], spin);
            blade(pose, buf, ROTOR_X[i], ROTOR_Y, ROTOR_Z[i], spin + (float) (Math.PI / 2));
        }
    }

    /** A thin flat propeller blade (a full diameter bar) at height {@code y}, rotating about {@code (cx,cz)}. */
    private void blade(PoseStack.Pose pose, VertexConsumer buf, float cx, float y, float cz, float angle) {
        float dx = Mth.cos(angle), dz = Mth.sin(angle);
        float px = -dz, pz = dx; // perpendicular (blade width axis)
        float ax = cx + dx * BLADE_LEN + px * BLADE_WID, az = cz + dz * BLADE_LEN + pz * BLADE_WID;
        float bx = cx + dx * BLADE_LEN - px * BLADE_WID, bz = cz + dz * BLADE_LEN - pz * BLADE_WID;
        float cx2 = cx - dx * BLADE_LEN - px * BLADE_WID, cz2 = cz - dz * BLADE_LEN - pz * BLADE_WID;
        float dx2 = cx - dx * BLADE_LEN + px * BLADE_WID, dz2 = cz - dz * BLADE_LEN + pz * BLADE_WID;
        int r = 0xB8, g = 0xBC, b = 0xC2; // light grey, like the previous hand-drawn drone
        // top + bottom winding so it's visible from both sides
        quad(pose, buf, ax, y, az, bx, y, bz, cx2, y, cz2, dx2, y, dz2, r, g, b);
        quad(pose, buf, dx2, y, dz2, cx2, y, cz2, bx, y, bz, ax, y, az, r, g, b);
    }

    /** Colour-only quad (debugQuads pipeline — no texture/light/overlay). */
    private static void quad(PoseStack.Pose pose, VertexConsumer buf,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int r, int g, int b) {
        buf.addVertex(pose, ax, ay, az).setColor(r, g, b, 255);
        buf.addVertex(pose, bx, by, bz).setColor(r, g, b, 255);
        buf.addVertex(pose, cx, cy, cz).setColor(r, g, b, 255);
        buf.addVertex(pose, dx, dy, dz).setColor(r, g, b, 255);
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
