package com.lowdragmc.kilagraphdemo.network;

import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: control a {@link DroneStationBlockEntity}'s run. Only the station's owner may drive it; the
 * server validates ownership, proximity and the block before acting. {@link Action#RUN} carries the
 * program graph to upload and start; the others ({@code PAUSE/RESUME/STEP/STOP}) carry an empty tag.
 */
public record C2SDroneControl(BlockPos pos, Action action, CompoundTag program) implements CustomPacketPayload {

    public enum Action { RUN, PAUSE, RESUME, STEP, STOP }

    public static final Type<C2SDroneControl> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "drone_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDroneControl> CODEC =
            StreamCodec.ofMember(C2SDroneControl::write, C2SDroneControl::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeNbt(program);
    }

    private static C2SDroneControl decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Action action = buf.readEnum(Action.class);
        CompoundTag program = buf.readNbt();
        return new C2SDroneControl(pos, action, program == null ? new CompoundTag() : program);
    }

    public static void execute(C2SDroneControl packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();
        if (!level.isLoaded(packet.pos) || !player.blockPosition().closerThan(packet.pos, 64)) return;
        if (!(level.getBlockEntity(packet.pos) instanceof DroneStationBlockEntity be)) return;
        // Only the owner may run/control their station.
        if (!be.isOwner(player.getUUID())) return;
        switch (packet.action) {
            case RUN -> {
                if (!be.startRun(packet.program)) {
                    // The only way startRun fails is no fertile soil directly beneath the station.
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.translatable("message.kilagraphdemo.drone_no_field"));
                }
            }
            case PAUSE -> be.pauseRun();
            case RESUME -> be.resumeRun();
            case STEP -> be.stepRun(packet.program);
            case STOP -> be.stopRun();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
