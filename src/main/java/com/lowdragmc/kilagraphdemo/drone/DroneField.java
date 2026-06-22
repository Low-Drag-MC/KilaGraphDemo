package com.lowdragmc.kilagraphdemo.drone;

/**
 * Geometry of the drone mini-game's <b>fixed</b> play field — the single source of truth shared by the
 * official scorer ({@link DroneScoring}), the in-world preview run
 * ({@link com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity#startRun}) and the renderer.
 *
 * <p>The field is a fixed {@link #SIZE}x{@link #SIZE} grid, unrelated to the blocks under the station
 * (fertile soil is purely decorative now). The station sits <b>outside</b> the field, one cell north of
 * its NW corner, at grid {@code (START_X, START_Z) = (0, -1)}; the drone starts there and its first move
 * must head south to fly into the field at {@code (0, 0)}.</p>
 */
public final class DroneField {

    private DroneField() {
    }

    /** Side length of the square play field — the only knob for field size. */
    public static final int SIZE = 9;
    public static final int WIDTH = SIZE;
    public static final int HEIGHT = SIZE;

    /** Drone/station start cell: one cell north of the field's NW corner, i.e. just OUTSIDE the field. */
    public static final int START_X = 0;
    public static final int START_Z = -1;

    /**
     * Local render offset from the station block to field origin {@code (0,0)}: since the station is at
     * grid {@code (START_X, START_Z)}, the field origin in station-local cells is {@code -start}. So the
     * field begins {@link #OFFSET_Z}=1 cell south of the station ({@link #OFFSET_X}=0 in X).
     */
    public static final int OFFSET_X = -START_X;
    public static final int OFFSET_Z = -START_Z;
}
