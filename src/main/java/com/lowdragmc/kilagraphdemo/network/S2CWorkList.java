package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import com.lowdragmc.kilagraphdemo.graph.ServerWorkEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** S2C: the full server work list (meta + likes) for the requesting player. */
public record S2CWorkList(CompoundTag data) implements CustomPacketPayload {
    public static final Type<S2CWorkList> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "work_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CWorkList> CODEC =
            StreamCodec.ofMember(S2CWorkList::write, S2CWorkList::decode);

    public static S2CWorkList of(List<ServerWorkEntry> entries) {
        ListTag list = new ListTag();
        entries.forEach(e -> list.add(e.toTag()));
        CompoundTag tag = new CompoundTag();
        tag.put("entries", list);
        return new S2CWorkList(tag);
    }

    public List<ServerWorkEntry> entries() {
        List<ServerWorkEntry> out = new ArrayList<>();
        for (Tag t : data.getListOrEmpty("entries")) {
            if (t instanceof CompoundTag ct) out.add(ServerWorkEntry.fromTag(ct));
        }
        return out;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    private static S2CWorkList decode(RegistryFriendlyByteBuf buf) {
        return new S2CWorkList(buf.readNbt());
    }

    public static void execute(S2CWorkList packet, IPayloadContext context) {
        ClientWorks.onListReceived(packet.entries());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
