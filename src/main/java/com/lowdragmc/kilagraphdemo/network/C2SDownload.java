package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.server.WorksSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** C2S: request a work's package; the server streams it back as {@link S2CWorkDataChunk}s. */
public record C2SDownload(String uid) implements CustomPacketPayload {
    public static final Type<C2SDownload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "download"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDownload> CODEC =
            StreamCodec.ofMember(C2SDownload::write, C2SDownload::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(uid);
    }

    private static C2SDownload decode(RegistryFriendlyByteBuf buf) {
        return new C2SDownload(buf.readUtf());
    }

    public static void execute(C2SDownload packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        WorksSavedData data = WorksSavedData.get((ServerLevel) player.level());
        CompoundTag payload = data.getPayload(packet.uid);
        if (payload == null) return;
        List<byte[]> chunks = Chunks.split(Chunks.toBytes(payload));
        for (int i = 0; i < chunks.size(); i++) {
            PacketDistributor.sendToPlayer(player, new S2CWorkDataChunk(packet.uid, i, chunks.size(), chunks.get(i)));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
