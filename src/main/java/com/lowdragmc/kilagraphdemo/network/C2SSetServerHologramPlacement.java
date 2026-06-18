package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.block.ServerHologramBlockEntity;
import com.lowdragmc.kilagraphdemo.graph.HologramPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: set the {@link HologramPlacement} (transform + spin) a {@link ServerHologramBlockEntity} renders with.
 * Like {@link C2SSetServerHologram}, only ops / creative players may change it; the server validates the
 * target block + proximity, then persists + syncs the change to all tracking clients via the block entity.
 */
public record C2SSetServerHologramPlacement(BlockPos pos, HologramPlacement placement) implements CustomPacketPayload {
    public static final Type<C2SSetServerHologramPlacement> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "set_server_hologram_placement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetServerHologramPlacement> CODEC =
            StreamCodec.ofMember(C2SSetServerHologramPlacement::write, C2SSetServerHologramPlacement::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        placement.write(buf);
    }

    private static C2SSetServerHologramPlacement decode(RegistryFriendlyByteBuf buf) {
        return new C2SSetServerHologramPlacement(buf.readBlockPos(), HologramPlacement.read(buf));
    }

    public static void execute(C2SSetServerHologramPlacement packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !player.isCreative()) return;
        ServerLevel level = (ServerLevel) player.level();
        if (!level.isLoaded(packet.pos) || !player.blockPosition().closerThan(packet.pos, 64)) return;
        if (!(level.getBlockEntity(packet.pos) instanceof ServerHologramBlockEntity be)) return;
        be.setPlacement(packet.placement);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
