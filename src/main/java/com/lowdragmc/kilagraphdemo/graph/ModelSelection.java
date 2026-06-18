package com.lowdragmc.kilagraphdemo.graph;

import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent;
import com.lowdragmc.kilagraphdemo.client.model.ObjContents;
import com.lowdragmc.kilagraphdemo.client.model.SubdividedContents;
import net.minecraft.nbt.CompoundTag;

/**
 * The geometry a work is displayed on: either a built-in primitive ({@code key} ∈
 * {@link SubdividedContents#QUAD}/{@code CUBE}/{@code SPHERE}) with a tessellation count, or a user-imported
 * {@link ObjContents#OBJ} mesh referenced by {@link #location} (a {@code kilagraphdemo:…} file) with an
 * optional UV {@link #flipV} and a {@link #transform} (offset/scale/rotation). Part of a {@link WorkPackage},
 * so a downloaded work renders on the same model its author chose (the OBJ bytes are bundled alongside).
 */
public record ModelSelection(String key, int subdivisions, String location, boolean flipV,
                             ModelTransform transform, float renderRadius) {

    /** Default cull radius (block units) — roughly the footprint of the default model + offset. */
    public static final float DEFAULT_RADIUS = 1.0f;

    public static final ModelSelection DEFAULT =
            new ModelSelection(SubdividedContents.CUBE, 1, "", false, ModelTransform.IDENTITY, DEFAULT_RADIUS);

    /** A built-in primitive selection. */
    public static ModelSelection builtin(String primitive, int subdivisions) {
        return new ModelSelection(primitive, Math.max(1, subdivisions), "", false, ModelTransform.IDENTITY, DEFAULT_RADIUS);
    }

    /** A custom-OBJ selection referencing the given {@code kilagraphdemo:…} location. */
    public static ModelSelection obj(String location) {
        return new ModelSelection(ObjContents.OBJ, 1, location, false, ModelTransform.IDENTITY, DEFAULT_RADIUS);
    }

    public boolean isObj() {
        return ObjContents.OBJ.equals(key);
    }

    public ModelSelection withLocation(String location) {
        return new ModelSelection(key, subdivisions, location, flipV, transform, renderRadius);
    }

    public ModelSelection withFlipV(boolean flipV) {
        return new ModelSelection(key, subdivisions, location, flipV, transform, renderRadius);
    }

    public ModelSelection withSubdivisions(int subdivisions) {
        return new ModelSelection(key, Math.max(1, subdivisions), location, flipV, transform, renderRadius);
    }

    public ModelSelection withTransform(ModelTransform transform) {
        return new ModelSelection(key, subdivisions, location, flipV, transform, renderRadius);
    }

    public ModelSelection withRenderRadius(float renderRadius) {
        return new ModelSelection(key, subdivisions, location, flipV, transform, Math.max(0f, renderRadius));
    }

    /** Short human label for detail panels, e.g. {@code "cube x4"} or {@code "OBJ teapot.obj"}. */
    public String describe() {
        if (isObj()) {
            int slash = location.lastIndexOf('/');
            String name = location.isEmpty() ? "(none)" : (slash >= 0 ? location.substring(slash + 1) : location);
            return "OBJ " + name;
        }
        return key + " x" + subdivisions;
    }

    /** The renderable geometry for this selection (client-side). */
    public KGPreviewContent toContent() {
        return isObj() ? ObjContents.of(location, flipV, transform) : SubdividedContents.of(key, subdivisions);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putInt("subdivisions", subdivisions);
        tag.putString("location", location);
        tag.putBoolean("flipV", flipV);
        tag.put("transform", transform.toTag());
        tag.putFloat("renderRadius", renderRadius);
        return tag;
    }

    public static ModelSelection fromTag(CompoundTag tag) {
        return new ModelSelection(
                tag.getStringOr("key", DEFAULT.key()),
                tag.getIntOr("subdivisions", DEFAULT.subdivisions()),
                tag.getStringOr("location", DEFAULT.location()),
                tag.getBooleanOr("flipV", DEFAULT.flipV()),
                ModelTransform.fromTag(tag.getCompoundOrEmpty("transform")),
                tag.getFloatOr("renderRadius", DEFAULT_RADIUS));
    }
}
