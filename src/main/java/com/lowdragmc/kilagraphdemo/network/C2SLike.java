package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.server.WorksSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: like / unlike a work (one like per player per uid). */
public record C2SLike(String uid, boolean like) implements CustomPacketPayload {
    public static final Type<C2SLike> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "like"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SLike> CODEC =
            StreamCodec.ofMember(C2SLike::write, C2SLike::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(uid);
        buf.writeBoolean(like);
    }

    private static C2SLike decode(RegistryFriendlyByteBuf buf) {
        return new C2SLike(buf.readUtf(), buf.readBoolean());
    }

    public static void execute(C2SLike packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        WorksSavedData data = WorksSavedData.get((ServerLevel) player.level());
        data.setLike(packet.uid, player.getUUID(), packet.like);
        ModNetworking.sendList(player);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
