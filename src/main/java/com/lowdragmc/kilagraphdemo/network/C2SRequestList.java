package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: ask the server for the current work list. */
public record C2SRequestList() implements CustomPacketPayload {
    public static final Type<C2SRequestList> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "request_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestList> CODEC =
            StreamCodec.unit(new C2SRequestList());

    public static void execute(C2SRequestList packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ModNetworking.sendList(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
