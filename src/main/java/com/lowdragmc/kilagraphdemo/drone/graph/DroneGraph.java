package com.lowdragmc.kilagraphdemo.drone.graph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraphdemo.Kilagraphdemo;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The node graph players program a farming drone with. It reuses KilaGraph's {@link BlueprintGraph}
 * execution and basic nodes (control flow, math, logic, collections, conversions) but <b>hides every
 * Minecraft-facing node group</b> — the drone interacts with the world only through the curated
 * {@code drone} nodes registered against this graph type (move/plant/harvest/clear/wait + sensors).
 */
public class DroneGraph extends BlueprintGraph {

    /** Drone-specific nodes are registered with {@code graphTypes = DroneGraph.class}. */
    public static final GraphNodeRegistry NODE_REGISTRY =
            GraphNodeRegistry.create(Identifier.fromNamespaceAndPath(Kilagraphdemo.MODID, "drone"),
                    DroneGraph.class);

    /** Minecraft-facing node groups from BlueprintGraph that players must not see. */
    private static final Set<String> EXCLUDED_GROUPS =
            Set.of("mc", "mc_entity", "mc_info", "mc_nbt", "mc_world");

    /** Direction-related nodes kept despite their excluded group — the drone moves by {@link net.minecraft.core.Direction}. */
    private static final Set<String> ALLOWED_NODE_NAMES =
            Set.of("mc_direction_const", "mc_direction_axis", "mc_direction_opposite", "info_direction");

    /** Individually-hidden nodes from otherwise-allowed groups: the generic print is replaced by {@code drone.print}. */
    private static final Set<String> EXCLUDED_NODE_NAMES = Set.of("exec_print");

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        List<Class<? extends Node>> nodes = new ArrayList<>();
        for (Class<? extends Node> cls : BlueprintGraph.NODE_REGISTRY.getNodeClasses()) {
            if (!isExcluded(cls)) nodes.add(cls);
        }
        nodes.addAll(NODE_REGISTRY.getNodeClasses());
        return List.copyOf(nodes);
    }

    private static boolean isExcluded(Class<? extends Node> cls) {
        NodeAttribute attr = cls.getAnnotation(NodeAttribute.class);
        if (attr == null) return false;
        if (EXCLUDED_NODE_NAMES.contains(attr.name())) return true;  // hide individually-excluded nodes
        if (ALLOWED_NODE_NAMES.contains(attr.name())) return false; // keep direction helpers
        return EXCLUDED_GROUPS.contains(attr.group());
    }
}
