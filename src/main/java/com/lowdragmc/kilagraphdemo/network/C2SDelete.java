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

/** C2S: delete the player's own work. */
public record C2SDelete(String uid) implements CustomPacketPayload {
    public static final Type<C2SDelete> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "delete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDelete> CODEC =
            StreamCodec.ofMember(C2SDelete::write, C2SDelete::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(uid);
    }

    private static C2SDelete decode(RegistryFriendlyByteBuf buf) {
        return new C2SDelete(buf.readUtf());
    }

    public static void execute(C2SDelete packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        WorksSavedData data = WorksSavedData.get((ServerLevel) player.level());
        data.delete(packet.uid, player);
        ModNetworking.sendList(player);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
