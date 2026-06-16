package com.lowdragmc.kilagraphdemo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A decorative hologram projector block. It can mount on any face (floor, ceiling, wall); the
 * {@link #FACING} property drives both the model rotation (blockstate json) and the light projection
 * orientation (block entity renderer). No gameplay logic.
 */
public class HologramBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    // A 7/16-tall slab covering the full footprint, rotated so it sits against the mounting face
    // (matches the blockstate model rotations).
    private static final Map<Direction, VoxelShape> SHAPES = Util.make(new EnumMap<>(Direction.class), map -> {
        map.put(Direction.UP, Block.box(0, 0, 0, 16, 7, 16));
        map.put(Direction.DOWN, Block.box(0, 9, 0, 16, 16, 16));
        map.put(Direction.NORTH, Block.box(0, 0, 9, 16, 16, 16));
        map.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 7));
        map.put(Direction.WEST, Block.box(9, 0, 0, 16, 16, 16));
        map.put(Direction.EAST, Block.box(0, 0, 0, 7, 16, 16));
    });

    public HologramBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Mount the projector against the clicked surface; it emits away from that surface.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HologramBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        // Phase 1: open the client-only hologram browser. The client class is referenced only inside
        // this client-side guard, so a dedicated server never classloads it. (Phase 2 switches to the
        // server-driven BlockUIMenuType flow.)
        if (level.isClientSide()) {
            com.lowdragmc.kilagraphdemo.client.editor.HologramScreens.openBrowse(level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
