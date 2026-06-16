package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the hologram block. It carries no persisted/synced data — but the client uses one
 * transient field, {@link #displayedWorkUid}, to remember which local work this hologram is currently
 * showing, so the browser can default-select it. This is deliberately client-only: not written to NBT,
 * not synced to the server (the display is purely a client-side runtime concept).
 */
public class HologramBlockEntity extends BlockEntity {

    /** Client-only: uid of the local work currently displayed here, or null for the default graph. */
    @Nullable
    private String displayedWorkUid;

    public HologramBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.HOLOGRAM_BE.get(), pos, state);
    }

    @Nullable
    public String getDisplayedWorkUid() {
        return displayedWorkUid;
    }

    public void setDisplayedWorkUid(@Nullable String uid) {
        this.displayedWorkUid = uid;
    }
}
