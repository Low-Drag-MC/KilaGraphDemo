package com.lowdragmc.kilagraphdemo.graph;

import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3f;

/**
 * A transform (offset / per-axis scale / Euler rotation in degrees), used two ways: baked into a custom OBJ
 * mesh by {@link com.lowdragmc.kilagraphdemo.client.model.ObjContents} (part of a {@link ModelSelection}), and
 * as a per-block hologram placement applied via the PoseStack (part of a {@link HologramPlacement}). Authors
 * can position/size/orient without re-exporting. Rotation order is X→Y→Z.
 */
public record ModelTransform(
        float offsetX, float offsetY, float offsetZ,
        float scaleX, float scaleY, float scaleZ,
        float rotX, float rotY, float rotZ) {

    public static final ModelTransform IDENTITY = new ModelTransform(0, 0, 0, 1, 1, 1, 0, 0, 0);

    public boolean isIdentity() {
        return this.equals(IDENTITY);
    }

    public ModelTransform withOffset(float x, float y, float z) {
        return new ModelTransform(x, y, z, scaleX, scaleY, scaleZ, rotX, rotY, rotZ);
    }

    public ModelTransform withScale(float x, float y, float z) {
        return new ModelTransform(offsetX, offsetY, offsetZ, x, y, z, rotX, rotY, rotZ);
    }

    public ModelTransform withRotation(float x, float y, float z) {
        return new ModelTransform(offsetX, offsetY, offsetZ, scaleX, scaleY, scaleZ, x, y, z);
    }

    // Vector3f bridges, for driving a Vector3fAccessor (3-field xyz row) in the editor UI.

    public Vector3f offsetVec() {
        return new Vector3f(offsetX, offsetY, offsetZ);
    }

    public Vector3f scaleVec() {
        return new Vector3f(scaleX, scaleY, scaleZ);
    }

    public Vector3f rotVec() {
        return new Vector3f(rotX, rotY, rotZ);
    }

    public ModelTransform withOffsetVec(Vector3f v) {
        return withOffset(v.x, v.y, v.z);
    }

    public ModelTransform withScaleVec(Vector3f v) {
        return withScale(v.x, v.y, v.z);
    }

    public ModelTransform withRotVec(Vector3f v) {
        return withRotation(v.x, v.y, v.z);
    }

    /** Largest absolute per-axis scale — a conservative multiplier for cull sizing. */
    public float maxScale() {
        return Math.max(Math.abs(scaleX), Math.max(Math.abs(scaleY), Math.abs(scaleZ)));
    }

    /** Length of the offset vector (block units) — for cull-box reach. */
    public float offsetLength() {
        return (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
    }

    /** Transform a position: scale, then rotate (X→Y→Z), then translate. Writes into {@code out} (len 3). */
    public void applyPosition(float x, float y, float z, float[] out) {
        rotate(x * scaleX, y * scaleY, z * scaleZ, out);
        out[0] += offsetX;
        out[1] += offsetY;
        out[2] += offsetZ;
    }

    /** Transform a normal: inverse-scale, rotate, then normalize. Writes into {@code out} (len 3). */
    public void applyNormal(float x, float y, float z, float[] out) {
        float nx = x / nonZero(scaleX), ny = y / nonZero(scaleY), nz = z / nonZero(scaleZ);
        rotate(nx, ny, nz, out);
        float len = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        if (len < 1.0e-6f) {
            out[0] = 0f;
            out[1] = 0f;
            out[2] = 1f;
        } else {
            out[0] /= len;
            out[1] /= len;
            out[2] /= len;
        }
    }

    private void rotate(float x, float y, float z, float[] out) {
        float cx = cos(rotX), sx = sin(rotX);
        float cy = cos(rotY), sy = sin(rotY);
        float cz = cos(rotZ), sz = sin(rotZ);
        // Rx
        float y1 = y * cx - z * sx, z1 = y * sx + z * cx, x1 = x;
        // Ry
        float x2 = x1 * cy + z1 * sy, z2 = -x1 * sy + z1 * cy, y2 = y1;
        // Rz
        out[0] = x2 * cz - y2 * sz;
        out[1] = x2 * sz + y2 * cz;
        out[2] = z2;
    }

    private static float cos(float deg) {
        return (float) Math.cos(Math.toRadians(deg));
    }

    private static float sin(float deg) {
        return (float) Math.sin(Math.toRadians(deg));
    }

    private static float nonZero(float v) {
        return Math.abs(v) < 1.0e-6f ? 1.0e-6f : v;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("ox", offsetX);
        tag.putFloat("oy", offsetY);
        tag.putFloat("oz", offsetZ);
        tag.putFloat("sx", scaleX);
        tag.putFloat("sy", scaleY);
        tag.putFloat("sz", scaleZ);
        tag.putFloat("rx", rotX);
        tag.putFloat("ry", rotY);
        tag.putFloat("rz", rotZ);
        return tag;
    }

    public static ModelTransform fromTag(CompoundTag tag) {
        return new ModelTransform(
                tag.getFloatOr("ox", 0f), tag.getFloatOr("oy", 0f), tag.getFloatOr("oz", 0f),
                tag.getFloatOr("sx", 1f), tag.getFloatOr("sy", 1f), tag.getFloatOr("sz", 1f),
                tag.getFloatOr("rx", 0f), tag.getFloatOr("ry", 0f), tag.getFloatOr("rz", 0f));
    }
}
