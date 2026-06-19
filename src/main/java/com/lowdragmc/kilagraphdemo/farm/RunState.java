package com.lowdragmc.kilagraphdemo.farm;

/**
 * Lifecycle of a drone programming station's run. The station is authoritative on the server; the
 * client mirrors this for rendering and UI button enablement.
 */
public enum RunState {
    /** No program running; the field is in its built (player-arranged) state. */
    IDLE,
    /** A program is executing on the server. */
    RUNNING,
    /** A run is paused (owner can resume, single-step, or stop). */
    PAUSED,
    /** A run reached the tick budget and was scored. */
    FINISHED
}
