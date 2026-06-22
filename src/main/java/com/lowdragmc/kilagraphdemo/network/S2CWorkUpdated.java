package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: a tiny "work {@code uid} was re-uploaded to {@code version}" notice, broadcast to every player when
 * an owned work is updated. Clients that don't have the uid ignore it; clients that do invalidate their
 * placed-block display caches so the next render re-pulls the new payload (the heavy graph itself is still
 * fetched lazily, per the existing {@link S2CWorkDataChunk} flow).
 */
public record S2CWorkUpdated(String uid, int version) implements CustomPacketPayload {
    public static final Type<S2CWorkUpdated> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "work_updated"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CWorkUpdated> CODEC =
            StreamCodec.ofMember(S2CWorkUpdated::write, S2CWorkUpdated::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(uid);
        buf.writeVarInt(version);
    }

    private static S2CWorkUpdated decode(RegistryFriendlyByteBuf buf) {
        return new S2CWorkUpdated(buf.readUtf(), buf.readVarInt());
    }

    public static void execute(S2CWorkUpdated packet, IPayloadContext context) {
        ClientWorks.onWorkUpdated(packet.uid, packet.version);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
