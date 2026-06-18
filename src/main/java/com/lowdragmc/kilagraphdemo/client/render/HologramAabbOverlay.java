package com.lowdragmc.kilagraphdemo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A transient debug overlay: draws a hologram's render bounding box (the cull AABB) as a world-space wireframe
 * for {@link #DURATION_MS}, so authors can see what {@code getRenderBoundingBox} the renderer reports for their
 * current render radius + placement. Each {@link #show} call refreshes the 10s window. Rendered from a
 * {@code SubmitCustomGeometryEvent} on the client; entries are keyed by block so re-showing just extends the timer.
 */
public final class HologramAabbOverlay {

    private static final long DURATION_MS = 10_000L;
    private static final int COLOR = 0xFFFFEE00; // opaque amber
    private static final float LINE_WIDTH = 4f;

    private record Entry(AABB box, long expiry) {
    }

    private static final Map<GlobalPos, Entry> ENTRIES = new HashMap<>();

    private HologramAabbOverlay() {
    }

    /** Show (or refresh) the wireframe for {@code box} at {@code pos} for the next {@link #DURATION_MS}. */
    public static void show(GlobalPos pos, AABB box) {
        ENTRIES.put(pos, new Entry(box, System.currentTimeMillis() + DURATION_MS));
    }

    public static void clearAll() {
        ENTRIES.clear();
    }

    /** Draw all live boxes for the current dimension; prune expired ones. Runs on the render thread. */
    public static void render(SubmitNodeCollector collector, PoseStack poseStack) {
        if (ENTRIES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        var dimension = mc.level.dimension();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z); // world coords → camera-relative
        for (Iterator<Map.Entry<GlobalPos, Entry>> it = ENTRIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<GlobalPos, Entry> e = it.next();
            if (e.getValue().expiry() <= now) {
                it.remove();
                continue;
            }
            if (!e.getKey().dimension().equals(dimension)) continue;
            AABB box = e.getValue().box();
            collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buf) -> drawBox(pose, buf, box));
        }
        poseStack.popPose();
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer buf, AABB b) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        // bottom rectangle
        line(pose, buf, x0, y0, z0, x1, y0, z0);
        line(pose, buf, x1, y0, z0, x1, y0, z1);
        line(pose, buf, x1, y0, z1, x0, y0, z1);
        line(pose, buf, x0, y0, z1, x0, y0, z0);
        // top rectangle
        line(pose, buf, x0, y1, z0, x1, y1, z0);
        line(pose, buf, x1, y1, z0, x1, y1, z1);
        line(pose, buf, x1, y1, z1, x0, y1, z1);
        line(pose, buf, x0, y1, z1, x0, y1, z0);
        // vertical edges
        line(pose, buf, x0, y0, z0, x0, y1, z0);
        line(pose, buf, x1, y0, z0, x1, y1, z0);
        line(pose, buf, x1, y0, z1, x1, y1, z1);
        line(pose, buf, x0, y0, z1, x0, y1, z1);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-6f) { nx /= len; ny /= len; nz /= len; }
        buf.addVertex(pose, x0, y0, z0).setColor(COLOR).setNormal(nx, ny, nz).setLineWidth(LINE_WIDTH);
        buf.addVertex(pose, x1, y1, z1).setColor(COLOR).setNormal(nx, ny, nz).setLineWidth(LINE_WIDTH);
    }
}
