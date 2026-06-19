package com.lowdragmc.kilagraphdemo.client.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only singleton that overrides the render camera while a drone station UI is open, so the right
 * half of the screen shows the farm from a free-flying vantage point instead of the player's eyes. Only
 * the <em>render</em> camera is moved (via {@link com.lowdragmc.kilagraphdemo.mixin.CameraMixin}); the
 * player entity is untouched, so dropping the override instantly restores the normal view.
 *
 * <p>{@link #activate} when the UI opens, {@link #deactivate} when it closes. Free-fly input
 * ({@link #fly}/{@link #rotate}/{@link #zoom}) keeps the camera within {@link #radius} blocks of the
 * station anchor.</p>
 */
public final class CameraOverrideManager {

    public static final CameraOverrideManager INSTANCE = new CameraOverrideManager();

    private boolean active;
    private Vec3 pos = Vec3.ZERO;
    private float yaw;
    private float pitch;
    /** Center the free-fly is tethered to (the station), and the max distance from it. */
    private Vec3 anchor = Vec3.ZERO;
    private double radius = 32;
    /** Vertical extent (in world blocks) the orthographic projection shows; "zoom" changes this. */
    private float orthoHeight = 16f;

    private CameraOverrideManager() {
    }

    public void activate(Vec3 pos, float yaw, float pitch, Vec3 anchor, double radius) {
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.anchor = anchor;
        this.radius = radius;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return active;
    }

    public Vec3 pos() {
        return pos;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Vertical world-space extent shown by the orthographic projection. */
    public float orthoHeight() {
        return orthoHeight;
    }

    /** Move relative to the current facing: {@code forward}/{@code strafe} are horizontal, {@code up} is world-Y. */
    public void fly(double forward, double strafe, double up) {
        if (forward == 0 && strafe == 0 && up == 0) return;
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad); // horizontal forward
        double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad); // strafe-right
        Vec3 delta = new Vec3(forward * fx + strafe * rx, up, forward * fz + strafe * rz);
        setPosClamped(pos.add(delta));
    }

    /** Look around: add to yaw, add to pitch (clamped to nearly straight up/down). */
    public void rotate(float dYaw, float dPitch) {
        this.yaw += dYaw;
        this.pitch = Mth.clamp(this.pitch + dPitch, -89f, 89f);
    }

    /** Zoom the orthographic view (mouse wheel): positive {@code amount} zooms in (smaller extent). */
    public void zoom(double amount) {
        if (amount == 0) return;
        orthoHeight = Mth.clamp(orthoHeight * (float) Math.pow(0.9, amount), 4f, 64f);
    }

    /** Set the camera position, tethered within {@link #radius} blocks of the {@link #anchor}. */
    private void setPosClamped(Vec3 target) {
        Vec3 offset = target.subtract(anchor);
        double len = offset.length();
        this.pos = len > radius ? anchor.add(offset.scale(radius / len)) : target;
    }
}
