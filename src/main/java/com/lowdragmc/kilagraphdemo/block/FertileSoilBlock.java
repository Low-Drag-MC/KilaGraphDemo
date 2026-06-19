package com.lowdragmc.kilagraphdemo.block;

import net.minecraft.world.level.block.Block;

/**
 * The plantable ground for the drone farm mini-game. Unlike vanilla farmland it needs no water and
 * never reverts — it is purely a marker that defines the playfield. A drone programming station
 * flood-fills the connected fertile soil beneath it to discover its field (see
 * {@code com.lowdragmc.kilagraphdemo.farm.FieldDetector}).
 *
 * <p>Model/texture currently reuse vanilla farmland as a placeholder.</p>
 */
public class FertileSoilBlock extends Block {
    public FertileSoilBlock(Properties properties) {
        super(properties);
    }
}
