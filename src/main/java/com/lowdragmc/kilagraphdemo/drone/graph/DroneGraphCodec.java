package com.lowdragmc.kilagraphdemo.drone.graph;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * NBT (de)serialization for a {@link DroneGraph}, used to persist the player's program on the station
 * block entity and ship it over the network. Mirrors KilaGraph's graph-model serialization recipe.
 */
public final class DroneGraphCodec {

    private DroneGraphCodec() {
    }

    public static CompoundTag toTag(DroneGraph graph, HolderLookup.Provider provider) {
        var output = TagValueOutput.createWithContext(ProblemReporter.Collector.DISCARDING, provider);
        graph.graphModel.serialize(output);
        return output.buildResult();
    }

    public static DroneGraph fromTag(CompoundTag tag, HolderLookup.Provider provider) {
        DroneGraph graph = new DroneGraph();
        graph.graphModel.deserialize(TagValueInput.create(ProblemReporter.Collector.DISCARDING, provider, tag));
        return graph;
    }
}
