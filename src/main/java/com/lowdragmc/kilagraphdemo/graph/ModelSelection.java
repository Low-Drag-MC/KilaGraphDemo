package com.lowdragmc.kilagraphdemo.graph;

import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent;
import com.lowdragmc.kilagraphdemo.client.model.SubdividedContents;
import net.minecraft.nbt.CompoundTag;

/**
 * The geometry a work is displayed on: a primitive ({@link SubdividedContents#QUAD}/{@code CUBE}/
 * {@code SPHERE}) and how finely it's tessellated. Part of a {@link WorkPackage}, so a downloaded work
 * renders on the same model its author chose.
 */
public record ModelSelection(String key, int subdivisions) {

    public static final ModelSelection DEFAULT = new ModelSelection(SubdividedContents.CUBE, 1);

    /** The renderable geometry for this selection (client-side). */
    public KGPreviewContent toContent() {
        return SubdividedContents.of(key, subdivisions);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putInt("subdivisions", subdivisions);
        return tag;
    }

    public static ModelSelection fromTag(CompoundTag tag) {
        return new ModelSelection(
                tag.getStringOr("key", DEFAULT.key()),
                tag.getIntOr("subdivisions", DEFAULT.subdivisions()));
    }
}
