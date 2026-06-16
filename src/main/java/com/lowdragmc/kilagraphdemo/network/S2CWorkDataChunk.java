package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: one chunk of a downloaded work's package bytes. */
public record S2CWorkDataChunk(String uid, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final Type<S2CWorkDataChunk> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "work_data_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CWorkDataChunk> CODEC =
            StreamCodec.ofMember(S2CWorkDataChunk::write, S2CWorkDataChunk::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(uid);
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeByteArray(data);
    }

    private static S2CWorkDataChunk decode(RegistryFriendlyByteBuf buf) {
        return new S2CWorkDataChunk(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray());
    }

    public static void execute(S2CWorkDataChunk packet, IPayloadContext context) {
        ClientWorks.onDownloadChunk(packet.uid, packet.index, packet.total, packet.data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
