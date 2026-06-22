package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.accessors.EnumAccessor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Editor configurator for a drone node's {@code direction} port that offers only the four <b>horizontal</b>
 * directions — the farm field is flat, so {@code UP}/{@code DOWN} are meaningless. Mirrors LDLib2's default
 * enum configurator ({@link com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.ITypeConfigurable#DEFAULT})
 * but with a restricted candidate list.
 *
 * <p>Client-only: referenced only from inside the {@code withConfigurable} lambda in
 * {@link com.lowdragmc.kilagraphdemo.drone.node.MoveNode}, so the server never classloads it.</p>
 */
public final class DirectionConfigurator {
    private DirectionConfigurator() {}

    private static final List<Direction> HORIZONTAL =
            List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

    public static IConfigurable horizontal(IFieldValueConfigurable vc) {
        return IConfigurable.create(father -> father.addConfigurator(
                EnumAccessor.<Direction>create(
                        "",
                        HORIZONTAL,
                        () -> vc.getValue() instanceof Direction d ? d : Direction.NORTH,
                        vc::setValue,
                        Direction.NORTH,
                        vc.forceUpdate())));
    }
}
