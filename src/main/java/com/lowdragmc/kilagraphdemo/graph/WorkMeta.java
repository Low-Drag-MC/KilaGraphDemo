package com.lowdragmc.kilagraphdemo.graph;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Metadata describing a work. The {@code uid} is stable across updates (so likes and the
 * "update available" check track the same work); {@code version} bumps on each re-upload.
 * Author fields are filled at upload time; locally-authored works leave them blank.
 */
public record WorkMeta(String uid, int version, String authorUuid, String authorName,
                       String title, String description, long firstUploadTime, long lastUpdateTime) {

    /** A fresh meta for a newly-authored local work (no author/upload info yet). */
    public static WorkMeta newLocal(String title) {
        long now = System.currentTimeMillis();
        return new WorkMeta(UUID.randomUUID().toString(), 1, "", "", title, "", now, now);
    }

    public WorkMeta withTitle(String newTitle) {
        return new WorkMeta(uid, version, authorUuid, authorName, newTitle, description, firstUploadTime, lastUpdateTime);
    }

    public WorkMeta withDescription(String newDescription) {
        return new WorkMeta(uid, version, authorUuid, authorName, title, newDescription, firstUploadTime, lastUpdateTime);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("uid", uid);
        tag.putInt("version", version);
        tag.putString("authorUuid", authorUuid);
        tag.putString("authorName", authorName);
        tag.putString("title", title);
        tag.putString("description", description);
        tag.putLong("firstUploadTime", firstUploadTime);
        tag.putLong("lastUpdateTime", lastUpdateTime);
        return tag;
    }

    public static WorkMeta fromTag(CompoundTag tag) {
        return new WorkMeta(
                tag.getStringOr("uid", UUID.randomUUID().toString()),
                tag.getIntOr("version", 1),
                tag.getStringOr("authorUuid", ""),
                tag.getStringOr("authorName", ""),
                tag.getStringOr("title", "Untitled"),
                tag.getStringOr("description", ""),
                tag.getLongOr("firstUploadTime", 0L),
                tag.getLongOr("lastUpdateTime", 0L));
    }
}
