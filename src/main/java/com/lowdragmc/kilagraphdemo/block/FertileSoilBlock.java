package com.lowdragmc.kilagraphdemo.block;

import net.minecraft.world.level.block.Block;

/**
 * Decorative ground for the drone farm mini-game. The play field is a fixed
 * {@link com.lowdragmc.kilagraphdemo.drone.DroneField} grid independent of any blocks, so this block is
 * now purely cosmetic — a visual mat to place a drone station on.
 *
 * <p>Model/texture currently reuse vanilla farmland as a placeholder.</p>
 */
public class FertileSoilBlock extends Block {
    public FertileSoilBlock(Properties properties) {
        super(properties);
    }
}
