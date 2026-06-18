package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.block.ServerHologramBlockEntity;
import com.lowdragmc.kilagraphdemo.server.WorksSavedData;
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
 * C2S: set (or clear, when {@code uid} is empty) the work a {@link ServerHologramBlockEntity} displays.
 * Only ops / creative players may change it; the server validates the target block, the player's proximity,
 * and that the work exists, then persists + syncs the change to all tracking clients via the block entity.
 */
public record C2SSetServerHologram(BlockPos pos, String uid) implements CustomPacketPayload {
    public static final Type<C2SSetServerHologram> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "set_server_hologram"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetServerHologram> CODEC =
            StreamCodec.ofMember(C2SSetServerHologram::write, C2SSetServerHologram::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(uid);
    }

    private static C2SSetServerHologram decode(RegistryFriendlyByteBuf buf) {
        return new C2SSetServerHologram(buf.readBlockPos(), buf.readUtf());
    }

    public static void execute(C2SSetServerHologram packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        // Only ops (gamemaster = level 2) / creative may change a shared server hologram.
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !player.isCreative()) return;
        ServerLevel level = (ServerLevel) player.level();
        // Anti-abuse: the block must be loaded and reasonably close to the player.
        if (!level.isLoaded(packet.pos) || !player.blockPosition().closerThan(packet.pos, 64)) return;
        if (!(level.getBlockEntity(packet.pos) instanceof ServerHologramBlockEntity be)) return;
        // Accept "clear" (empty) or any published work.
        if (!packet.uid.isEmpty() && !WorksSavedData.get(level).exists(packet.uid)) return;
        be.setDisplayedWorkUid(packet.uid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
