package com.lowdragmc.kilagraphdemo.graph;

import net.minecraft.nbt.CompoundTag;

/**
 * A work as seen from the server: its authoritative {@link WorkMeta} plus server-derived fields
 * (like count, and whether the requesting player has liked it). Sent S2C to populate the browser's
 * server list / meta panel. The graph payload itself is fetched separately on download.
 */
public record ServerWorkEntry(WorkMeta meta, int likeCount, boolean likedByMe) {

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("meta", meta.toTag());
        tag.putInt("likeCount", likeCount);
        tag.putBoolean("likedByMe", likedByMe);
        return tag;
    }

    public static ServerWorkEntry fromTag(CompoundTag tag) {
        return new ServerWorkEntry(
                WorkMeta.fromTag(tag.getCompoundOrEmpty("meta")),
                tag.getIntOr("likeCount", 0),
                tag.getBooleanOr("likedByMe", false));
    }
}
