package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.server.WorksSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers the mod's network payloads and provides server-side send helpers. Client-side sends live in
 * {@link com.lowdragmc.kilagraphdemo.client.ClientWorks} (they use the client-only packet distributor).
 */
public final class ModNetworking {

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(Kilagraphdemo.MODID);
        registrar.playToServer(C2SRequestList.TYPE, C2SRequestList.CODEC, C2SRequestList::execute);
        registrar.playToServer(C2SUploadChunk.TYPE, C2SUploadChunk.CODEC, C2SUploadChunk::execute);
        registrar.playToServer(C2SDownload.TYPE, C2SDownload.CODEC, C2SDownload::execute);
        registrar.playToServer(C2SDelete.TYPE, C2SDelete.CODEC, C2SDelete::execute);
        registrar.playToServer(C2SLike.TYPE, C2SLike.CODEC, C2SLike::execute);
        registrar.playToServer(C2SSetServerHologram.TYPE, C2SSetServerHologram.CODEC, C2SSetServerHologram::execute);
        registrar.playToServer(C2SSetServerHologramPlacement.TYPE, C2SSetServerHologramPlacement.CODEC,
                C2SSetServerHologramPlacement::execute);
        // SlideShow projector-graph control — only when SlideShow is present (the projector block entity that
        // carries the state only exists then). Both sides evaluate this identically, so the protocol stays symmetric.
        if (net.neoforged.fml.ModList.get().isLoaded("slide_show")) {
            registrar.playToServer(C2SSetProjectorGraph.TYPE, C2SSetProjectorGraph.CODEC, C2SSetProjectorGraph::execute);
        }
        registrar.playToClient(S2CWorkList.TYPE, S2CWorkList.CODEC, S2CWorkList::execute);
        registrar.playToClient(S2CWorkDataChunk.TYPE, S2CWorkDataChunk.CODEC, S2CWorkDataChunk::execute);
    }

    /** Send the current work list (as seen by {@code player}) to that player. */
    public static void sendList(ServerPlayer player) {
        WorksSavedData data = WorksSavedData.get((ServerLevel) player.level());
        PacketDistributor.sendToPlayer(player, S2CWorkList.of(data.listEntries(player.getUUID())));
    }
}
