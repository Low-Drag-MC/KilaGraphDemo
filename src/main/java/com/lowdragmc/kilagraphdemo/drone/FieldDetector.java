package com.lowdragmc.kilagraphdemo.drone;

import com.lowdragmc.kilagraphdemo.block.FertileSoilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Discovers a drone farm's playfield by flood-filling the connected {@link FertileSoilBlock} cells
 * starting from the block directly beneath a programming station. The fill is 4-connected on a single
 * Y layer; the result is a rectangular {@link Field} (bounding box + fertile mask) that maps directly
 * onto a {@link com.lowdragmc.kilagraphdemo.farm.FarmSimulation} grid.
 */
public final class FieldDetector {

    /** Safety cap so a player can't define an absurdly large field. */
    public static final int MAX_CELLS = 21 * 21;

    private FieldDetector() {
    }

    /**
     * The detected field: a rectangular region on layer {@code y} whose {@code fertile} mask marks
     * which cells are actually fertile soil. Local coordinates are {@code (x, z)} with
     * {@code 0 <= x < width}, {@code 0 <= z < height}; world position is {@code (originX + x, y, originZ + z)}.
     */
    public record Field(int originX, int y, int originZ, int width, int height, boolean[] fertile) {
        public boolean isFertileLocal(int x, int z) {
            return x >= 0 && x < width && z >= 0 && z < height && fertile[z * width + x];
        }

        public BlockPos toWorld(int x, int z) {
            return new BlockPos(originX + x, y, originZ + z);
        }

        public int cellCount() {
            int c = 0;
            for (boolean b : fertile) if (b) c++;
            return c;
        }
    }

    /**
     * Detect the field beneath {@code stationPos}. Returns {@code null} if the block directly below is
     * not fertile soil (no field), or if the connected region exceeds {@link #MAX_CELLS}.
     */
    @Nullable
    public static Field detect(BlockGetter level, BlockPos stationPos) {
        BlockPos start = stationPos.below();
        if (!isFertile(level, start)) return null;

        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        found.add(start.immutable());
        int y = start.getY();

        while (!queue.isEmpty()) {
            if (found.size() > MAX_CELLS) return null;
            BlockPos p = queue.poll();
            for (BlockPos n : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
                BlockPos np = n.immutable();
                if (found.contains(np)) continue;
                if (isFertile(level, np)) {
                    found.add(np);
                    queue.add(np);
                }
            }
        }

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : found) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;
        boolean[] mask = new boolean[width * height];
        for (BlockPos p : found) {
            mask[(p.getZ() - minZ) * width + (p.getX() - minX)] = true;
        }
        return new Field(minX, y, minZ, width, height, mask);
    }

    private static boolean isFertile(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FertileSoilBlock;
    }
}
