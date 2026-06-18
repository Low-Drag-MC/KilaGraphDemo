package com.lowdragmc.kilagraphdemo.block;

import com.lowdragmc.kilagraphdemo.ModRegistries;
import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the {@link ServerHologramBlock}. It holds a single piece of state — the uid of the
 * server work this block displays — which is both <b>persisted</b> (server save) and <b>synced</b> to every
 * client tracking the chunk:
 * <ul>
 *   <li>{@link #saveAdditional}/{@link #loadAdditional} persist it to disk.</li>
 *   <li>{@link #getUpdateTag} carries it in the initial chunk sync (so players entering the chunk get it).</li>
 *   <li>{@link #getUpdatePacket} pushes live updates; {@link #setDisplayedWorkUid} triggers that push by
 *       marking the entity changed and calling {@code sendBlockUpdated}.</li>
 * </ul>
 */
public class ServerHologramBlockEntity extends BlockEntity {

    private static final String KEY_WORK = "work";
    private static final String KEY_PLACEMENT = "placement";

    /** The displayed server work's uid; empty means "nothing" (the block shows only the cone). */
    private String displayedWorkUid = "";
    /** The per-block placement (transform + spin) — persisted and synced like the work uid. */
    private HologramPlacement placement = HologramPlacement.DEFAULT;

    public ServerHologramBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.SERVER_HOLOGRAM_BE.get(), pos, state);
    }

    public String getDisplayedWorkUid() {
        return displayedWorkUid;
    }

    public HologramPlacement getPlacement() {
        return placement;
    }

    /** Server-side: set the displayed work and push it to disk + all tracking clients. */
    public void setDisplayedWorkUid(String uid) {
        this.displayedWorkUid = uid == null ? "" : uid;
        sync();
    }

    /** Server-side: set the placement and push it to disk + all tracking clients. */
    public void setPlacement(HologramPlacement placement) {
        this.placement = placement == null ? HologramPlacement.DEFAULT : placement;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(KEY_WORK, displayedWorkUid);
        output.store(KEY_PLACEMENT, CompoundTag.CODEC, placement.toTag());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        displayedWorkUid = input.getStringOr(KEY_WORK, "");
        placement = input.read(KEY_PLACEMENT, CompoundTag.CODEC)
                .map(HologramPlacement::fromTag).orElse(HologramPlacement.DEFAULT);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putString(KEY_WORK, displayedWorkUid);
        tag.put(KEY_PLACEMENT, placement.toTag());
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
