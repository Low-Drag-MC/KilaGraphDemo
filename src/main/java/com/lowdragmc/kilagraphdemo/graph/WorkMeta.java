package com.lowdragmc.kilagraphdemo.graph;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Metadata describing a work. The {@code uid} is stable across updates (so likes and the
 * "update available" check track the same work); {@code version} bumps on each re-upload. The
 * {@code kind} distinguishes what consumes the work — a {@link #KIND_HOLOGRAM hologram} display vs a
 * {@link #KIND_SLIDESHOW SlideShow} projector graph — so each browser shows only its own works.
 * Author fields are filled at upload time; locally-authored works leave them blank.
 */
public record WorkMeta(String uid, int version, String authorUuid, String authorName,
                       String title, String description, long firstUploadTime, long lastUpdateTime,
                       String kind) {

    /** A hologram display work (the default kind). */
    public static final String KIND_HOLOGRAM = "hologram";
    /** A SlideShow projector render-type graph work. */
    public static final String KIND_SLIDESHOW = "slideshow";

    /** A fresh meta for a newly-authored local hologram work (no author/upload info yet). */
    public static WorkMeta newLocal(String title) {
        return newLocal(title, KIND_HOLOGRAM);
    }

    /** A fresh meta for a newly-authored local work of the given {@code kind}. */
    public static WorkMeta newLocal(String title, String kind) {
        long now = System.currentTimeMillis();
        return new WorkMeta(UUID.randomUUID().toString(), 1, "", "", title, "", now, now, kind);
    }

    public WorkMeta withTitle(String newTitle) {
        return new WorkMeta(uid, version, authorUuid, authorName, newTitle, description, firstUploadTime, lastUpdateTime, kind);
    }

    public WorkMeta withDescription(String newDescription) {
        return new WorkMeta(uid, version, authorUuid, authorName, title, newDescription, firstUploadTime, lastUpdateTime, kind);
    }

    public boolean isSlideShow() {
        return KIND_SLIDESHOW.equals(kind);
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
        tag.putString("kind", kind);
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
                tag.getLongOr("lastUpdateTime", 0L),
                tag.getStringOr("kind", KIND_HOLOGRAM));
    }
}
