package com.lowdragmc.kilagraphdemo.farm;

/**
 * Deterministic, Minecraft-free simulation of the pumpkin farm: growth, rotting, greedy
 * largest-square merging and scoring on a rectangular grid of fertile/non-fertile cells.
 *
 * <p>All randomness (if any) is seeded so a given program produces an identical score every run,
 * which keeps the leaderboard fair. Drive it one game tick at a time via {@link #tick()}; the drone
 * acts through {@link #plant}, {@link #harvest} and {@link #clear}.</p>
 */
public class FarmSimulation {

    private final int width;
    private final int height;
    private final FarmConfig config;
    private final boolean[] fertile;
    private final Stage[] stage;
    /** Ticks elapsed in the current stage (growing toward ripe, or fresh-since-ripe). */
    private final int[] age;
    /** Randomized target duration for the current stage of each cell (grow time, then fresh time). */
    private final int[] threshold;
    /** 1 for a base pumpkin; N for the core cell of an N x N merged pumpkin. */
    private final int[] mergeSize;
    /** -1 when not part of a merged pumpkin; otherwise the index of that pumpkin's core cell. */
    private final int[] coreIdx;
    /** Seeded RNG for per-pumpkin grow/rot jitter — fixed seed keeps a program's score reproducible. */
    private final java.util.Random random;
    private int score;

    private FarmSimulation(int width, int height, FarmConfig config, boolean[] fertile, long seed) {
        this.width = width;
        this.height = height;
        this.config = config;
        this.fertile = fertile;
        this.stage = new Stage[width * height];
        this.age = new int[width * height];
        this.threshold = new int[width * height];
        this.mergeSize = new int[width * height];
        this.coreIdx = new int[width * height];
        this.random = new java.util.Random(seed);
        java.util.Arrays.fill(this.stage, Stage.EMPTY);
        java.util.Arrays.fill(this.mergeSize, 1);
        java.util.Arrays.fill(this.coreIdx, -1);
    }

    /** A simulation whose every cell is fertile (handy for tests and rectangular fields). */
    public static FarmSimulation allFertile(int width, int height, FarmConfig config) {
        return allFertile(width, height, config, 0L);
    }

    /** As {@link #allFertile(int, int, FarmConfig)} but with an explicit seed for the grow/rot jitter. */
    public static FarmSimulation allFertile(int width, int height, FarmConfig config, long seed) {
        boolean[] fertile = new boolean[width * height];
        java.util.Arrays.fill(fertile, true);
        return new FarmSimulation(width, height, config, fertile, seed);
    }

    /**
     * A simulation over a rectangular grid where {@code fertile[z*width + x]} marks which cells are
     * plantable. The mask is copied. Used by the real field, detected by flood-filling fertile soil.
     */
    public static FarmSimulation of(int width, int height, boolean[] fertile, FarmConfig config) {
        return of(width, height, fertile, config, 0L);
    }

    /** As {@link #of(int, int, boolean[], FarmConfig)} but with an explicit seed for the grow/rot jitter. */
    public static FarmSimulation of(int width, int height, boolean[] fertile, FarmConfig config, long seed) {
        if (fertile.length != width * height) {
            throw new IllegalArgumentException("fertile mask length " + fertile.length
                    + " != " + width + "x" + height);
        }
        return new FarmSimulation(width, height, config, fertile.clone(), seed);
    }

    /**
     * Draw a stage duration from a Gaussian centred on {@code mean} with standard deviation {@code jitter},
     * clamped to {@code [max(1, mean-2σ), mean+2σ]} so it stays positive and in a sensible range. With
     * {@code jitter <= 0} this is just {@code mean} (deterministic, for tests).
     */
    private int sampleDuration(int mean, int jitter) {
        if (jitter <= 0) return Math.max(1, mean);
        double v = mean + random.nextGaussian() * jitter;
        int lo = Math.max(1, mean - 2 * jitter);
        int hi = mean + 2 * jitter;
        return (int) Math.round(Math.max(lo, Math.min(hi, v)));
    }

    /** Field width in cells. */
    public int getWidth() {
        return width;
    }

    /** Field height in cells. */
    public int getHeight() {
        return height;
    }

    private int idx(int x, int z) {
        return z * width + x;
    }

    private boolean inBounds(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < height;
    }

    /** Whether a cell is part of the field (plantable). */
    public boolean isFertile(int x, int z) {
        return inBounds(x, z) && fertile[idx(x, z)];
    }

    /** Plant a seed on an empty fertile cell. Returns whether anything was planted. */
    public boolean plant(int x, int z) {
        if (!isFertile(x, z) || stage[idx(x, z)] != Stage.EMPTY) return false;
        int i = idx(x, z);
        stage[i] = Stage.GROWING;
        age[i] = 0;
        threshold[i] = sampleDuration(config.growTicks(), config.growJitter()); // this pumpkin's grow time
        return true;
    }

    /** Advance the whole farm by one game tick: growth and (later) rotting + merging. */
    public void tick() {
        for (int i = 0; i < stage.length; i++) {
            if (stage[i] == Stage.GROWING) {
                if (++age[i] >= threshold[i]) {
                    stage[i] = Stage.RIPE;
                    age[i] = 0;
                    threshold[i] = sampleDuration(config.freshTicks(), config.rotJitter()); // fresh window
                }
            } else if (stage[i] == Stage.RIPE) {
                if (++age[i] >= threshold[i]) {
                    stage[i] = Stage.ROTTEN;
                    age[i] = 0;
                }
            }
        }
        tryMerge();
    }

    /**
     * Greedily merge filled N x N squares of ripe base pumpkins into single big pumpkins, largest
     * first (N from {@code maxMergeSize} down to 2). The top-left cell of a square becomes the core
     * (holding {@code mergeSize = N}); the rest become {@link Stage#MERGED_MEMBER} pointing at it.
     */
    private void tryMerge() {
        for (int n = Math.min(config.maxMergeSize(), Math.min(width, height)); n >= 2; n--) {
            for (int z0 = 0; z0 + n <= height; z0++) {
                for (int x0 = 0; x0 + n <= width; x0++) {
                    if (isBaseRipeSquare(x0, z0, n)) {
                        merge(x0, z0, n);
                    }
                }
            }
        }
    }

    private boolean isBaseRipeSquare(int x0, int z0, int n) {
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                int i = idx(x0 + dx, z0 + dz);
                if (stage[i] != Stage.RIPE || mergeSize[i] != 1 || coreIdx[i] != -1) return false;
            }
        }
        return true;
    }

    private void merge(int x0, int z0, int n) {
        int core = idx(x0, z0);
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                int i = idx(x0 + dx, z0 + dz);
                if (i == core) continue;
                stage[i] = Stage.MERGED_MEMBER;
                coreIdx[i] = core;
                age[i] = 0;
            }
        }
        mergeSize[core] = n;
        stage[core] = Stage.RIPE;
        age[core] = 0; // big pumpkin starts its own fresh window
        threshold[core] = sampleDuration(config.freshTicks(), config.rotJitter());
    }

    /**
     * Harvest the pumpkin at a cell, banking its score and clearing it. Returns the score gained
     * (0 if there is nothing ripe to harvest).
     */
    public int harvest(int x, int z) {
        if (!inBounds(x, z)) return 0;
        int core = resolveCore(idx(x, z));
        if (stage[core] != Stage.RIPE) return 0;
        int n = mergeSize[core];
        int gained = scoreFor(n);
        clearBlock(core, n);
        score += gained;
        return gained;
    }

    /** Score awarded for harvesting a pumpkin of merge size N: 1 for a base pumpkin, else N^3. */
    public static int scoreFor(int n) {
        return n <= 1 ? 1 : n * n * n;
    }

    /** Resolve a cell index to the core of its merged pumpkin (or itself if not a member). */
    private int resolveCore(int i) {
        return coreIdx[i] != -1 ? coreIdx[i] : i;
    }

    /** Clear the N x N block whose core is at index {@code core}, resetting cells to empty base. */
    private void clearBlock(int core, int n) {
        int x0 = core % width, z0 = core / width;
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                int i = idx(x0 + dx, z0 + dz);
                stage[i] = Stage.EMPTY;
                age[i] = 0;
                mergeSize[i] = 1;
                coreIdx[i] = -1;
            }
        }
    }

    /** Total banked score so far. */
    public int getScore() {
        return score;
    }

    /** Remove whatever occupies a cell (crop, ripe/rotten pumpkin, or a whole merged block). */
    public boolean clear(int x, int z) {
        if (!inBounds(x, z) || stage[idx(x, z)] == Stage.EMPTY) return false;
        int core = resolveCore(idx(x, z));
        clearBlock(core, mergeSize[core]);
        return true;
    }

    /** The lifecycle stage of a cell. */
    public Stage getStage(int x, int z) {
        if (!inBounds(x, z)) return Stage.EMPTY;
        return stage[idx(x, z)];
    }

    /** The merge size of a cell: 1 for a base pumpkin, N for the core of an N x N merged pumpkin. */
    public int getMergeSize(int x, int z) {
        if (!inBounds(x, z)) return 1;
        return mergeSize[idx(x, z)];
    }
}
