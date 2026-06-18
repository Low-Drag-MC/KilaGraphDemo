package com.lowdragmc.kilagraphdemo.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Per-placed-block hologram placement: a {@link ModelTransform} (translate / scale / rotate the whole
 * projection) plus a {@link #spinDegreesPerTick} auto-spin speed (0 disables). Distinct from a work's
 * {@link ModelSelection#transform()} (which is baked into the OBJ mesh): this positions the hologram in the
 * world and is applied via the PoseStack. Runtime-only for the regular hologram; persisted + synced for the
 * server hologram. Headless-safe (no client imports), so it lives with the block entity / packet code.
 */
public record HologramPlacement(ModelTransform transform, float spinDegreesPerTick) {

    /** Default spin (degrees/tick) — matches the historical fixed auto-spin. */
    public static final float DEFAULT_SPIN = 2f;

    public static final HologramPlacement DEFAULT = new HologramPlacement(ModelTransform.IDENTITY, DEFAULT_SPIN);

    public HologramPlacement withTransform(ModelTransform transform) {
        return new HologramPlacement(transform, spinDegreesPerTick);
    }

    public HologramPlacement withSpin(float spinDegreesPerTick) {
        return new HologramPlacement(transform, spinDegreesPerTick);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("transform", transform.toTag());
        tag.putFloat("spin", spinDegreesPerTick);
        return tag;
    }

    public static HologramPlacement fromTag(CompoundTag tag) {
        return new HologramPlacement(
                ModelTransform.fromTag(tag.getCompoundOrEmpty("transform")),
                tag.getFloatOr("spin", DEFAULT_SPIN));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        ModelTransform t = transform;
        buf.writeFloat(t.offsetX());
        buf.writeFloat(t.offsetY());
        buf.writeFloat(t.offsetZ());
        buf.writeFloat(t.scaleX());
        buf.writeFloat(t.scaleY());
        buf.writeFloat(t.scaleZ());
        buf.writeFloat(t.rotX());
        buf.writeFloat(t.rotY());
        buf.writeFloat(t.rotZ());
        buf.writeFloat(spinDegreesPerTick);
    }

    public static HologramPlacement read(RegistryFriendlyByteBuf buf) {
        ModelTransform t = new ModelTransform(
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new HologramPlacement(t, buf.readFloat());
    }
}
