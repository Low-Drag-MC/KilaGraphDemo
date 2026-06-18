package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.server.WorksSavedData;
import com.lowdragmc.kilagraphdemo.slideshow.IProjectorGraphHolder;
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
 * C2S: set (or clear, when {@code uid} is empty) the {@code SlideShowGraph} work a projector renders
 * through. Only ops / creative players may change it; the server validates the target (a projector — i.e. a
 * block entity carrying the {@link IProjectorGraphHolder} state added by our mixin), the player's proximity,
 * and that the work exists, then persists + syncs the change via the block entity. References the projector
 * only through {@link IProjectorGraphHolder}, so this compiles and registers even without SlideShow present.
 */
public record C2SSetProjectorGraph(BlockPos pos, String uid) implements CustomPacketPayload {
    public static final Type<C2SSetProjectorGraph> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "set_projector_graph"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetProjectorGraph> CODEC =
            StreamCodec.ofMember(C2SSetProjectorGraph::write, C2SSetProjectorGraph::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(uid);
    }

    private static C2SSetProjectorGraph decode(RegistryFriendlyByteBuf buf) {
        return new C2SSetProjectorGraph(buf.readBlockPos(), buf.readUtf());
    }

    public static void execute(C2SSetProjectorGraph packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        // Only ops (gamemaster = level 2) / creative may drive a projector's graph.
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !player.isCreative()) return;
        ServerLevel level = (ServerLevel) player.level();
        // Anti-abuse: the block must be loaded and reasonably close to the player.
        if (!level.isLoaded(packet.pos) || !player.blockPosition().closerThan(packet.pos, 64)) return;
        if (!(level.getBlockEntity(packet.pos) instanceof IProjectorGraphHolder holder)) return;
        // Accept "clear" (empty) or any published work.
        if (!packet.uid.isEmpty() && !WorksSavedData.get(level).exists(packet.uid)) return;
        holder.kilagraphdemo$setGraphWorkUid(packet.uid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
