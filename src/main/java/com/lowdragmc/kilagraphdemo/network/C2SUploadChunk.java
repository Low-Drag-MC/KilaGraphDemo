package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.server.UploadAssembler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: one chunk of the player's uploaded work package. The server reassembles by transfer id. */
public record C2SUploadChunk(String transferId, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final Type<C2SUploadChunk> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "upload_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUploadChunk> CODEC =
            StreamCodec.ofMember(C2SUploadChunk::write, C2SUploadChunk::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(transferId);
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeByteArray(data);
    }

    private static C2SUploadChunk decode(RegistryFriendlyByteBuf buf) {
        return new C2SUploadChunk(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray());
    }

    public static void execute(C2SUploadChunk packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            UploadAssembler.accept(player, packet.transferId, packet.index, packet.total, packet.data);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
