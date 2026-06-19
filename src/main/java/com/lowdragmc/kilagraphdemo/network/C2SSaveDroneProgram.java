package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: persist the owner's edited program graph onto the station block entity (server source of truth).
 * Only the station owner may save.
 */
public record C2SSaveDroneProgram(BlockPos pos, CompoundTag program) implements CustomPacketPayload {
    public static final Type<C2SSaveDroneProgram> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "save_drone_program"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSaveDroneProgram> CODEC =
            StreamCodec.ofMember(C2SSaveDroneProgram::write, C2SSaveDroneProgram::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeNbt(program);
    }

    private static C2SSaveDroneProgram decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag tag = buf.readNbt();
        return new C2SSaveDroneProgram(pos, tag == null ? new CompoundTag() : tag);
    }

    public static void execute(C2SSaveDroneProgram packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();
        if (!level.isLoaded(packet.pos) || !player.blockPosition().closerThan(packet.pos, 64)) return;
        if (!(level.getBlockEntity(packet.pos) instanceof DroneStationBlockEntity be)) return;
        if (!be.isOwner(player.getUUID())) return;
        be.setProgram(packet.program);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
