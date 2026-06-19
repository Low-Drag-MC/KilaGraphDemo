package com.lowdragmc.kilagraphdemo.farm;

/**
 * Lifecycle state of a single farm cell. Pure game logic — no Minecraft dependency, so the
 * simulation can be unit-tested in isolation.
 */
public enum Stage {
    /** Nothing planted. */
    EMPTY,
    /** A pumpkin is growing toward ripeness (sub-stage derived from growth ticks). */
    GROWING,
    /** A ripe pumpkin, ready to harvest or merge. */
    RIPE,
    /** A ripe pumpkin left too long; worthless until cleared. */
    ROTTEN,
    /** A non-core cell occupied by a merged pumpkin whose core lives elsewhere. */
    MERGED_MEMBER
}
