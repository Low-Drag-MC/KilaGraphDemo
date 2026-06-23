package com.lowdragmc.kilagraphdemo;

import com.lowdragmc.kilagraphdemo.block.DroneRankingBlock;
import com.lowdragmc.kilagraphdemo.block.DroneRankingBlockEntity;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlock;
import com.lowdragmc.kilagraphdemo.block.DroneStationBlockEntity;
import com.lowdragmc.kilagraphdemo.block.FertileSoilBlock;
import com.lowdragmc.kilagraphdemo.block.HologramBlock;
import com.lowdragmc.kilagraphdemo.block.HologramBlockEntity;
import com.lowdragmc.kilagraphdemo.block.ServerHologramBlock;
import com.lowdragmc.kilagraphdemo.block.ServerHologramBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central registry holder for the demo's blocks, items, block entities and creative tab.
 */
public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Kilagraphdemo.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Kilagraphdemo.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Kilagraphdemo.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Kilagraphdemo.MODID);

    // Hologram block + item + block entity.
    public static final DeferredBlock<HologramBlock> HOLOGRAM_BLOCK = BLOCKS.registerBlock(
            "hologram",
            HologramBlock::new,
            p -> p.noOcclusion().strength(2f).lightLevel(state -> 7));

    public static final DeferredItem<?> HOLOGRAM_ITEM = ITEMS.registerSimpleBlockItem("hologram", HOLOGRAM_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HologramBlockEntity>> HOLOGRAM_BE =
            BLOCK_ENTITIES.register("hologram",
                    () -> new BlockEntityType<>(HologramBlockEntity::new, HOLOGRAM_BLOCK.get()));

    // Server hologram: like the hologram, but its displayed work is stored/synced by the server and
    // lazily pulled per client. Reuses the same model/textures for now.
    public static final DeferredBlock<ServerHologramBlock> SERVER_HOLOGRAM_BLOCK = BLOCKS.registerBlock(
            "server_hologram",
            ServerHologramBlock::new,
            p -> p.noOcclusion().strength(2f).lightLevel(state -> 7));

    public static final DeferredItem<?> SERVER_HOLOGRAM_ITEM =
            ITEMS.registerSimpleBlockItem("server_hologram", SERVER_HOLOGRAM_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServerHologramBlockEntity>> SERVER_HOLOGRAM_BE =
            BLOCK_ENTITIES.register("server_hologram",
                    () -> new BlockEntityType<>(ServerHologramBlockEntity::new, SERVER_HOLOGRAM_BLOCK.get()));

    // --- Drone Farm mini-game -------------------------------------------------------------------

    // Fertile soil: the plantable playfield (placeholder model reuses vanilla farmland).
    public static final DeferredBlock<FertileSoilBlock> FERTILE_SOIL_BLOCK = BLOCKS.registerBlock(
            "fertile_soil",
            FertileSoilBlock::new,
            p -> p.strength(0.6f));

    public static final DeferredItem<?> FERTILE_SOIL_ITEM =
            ITEMS.registerSimpleBlockItem("fertile_soil", FERTILE_SOIL_BLOCK);

    // Drone programming station: owns the virtual drone + drives the farming run.
    public static final DeferredBlock<DroneStationBlock> DRONE_STATION_BLOCK = BLOCKS.registerBlock(
            "drone_station",
            DroneStationBlock::new,
            p -> p.noOcclusion().strength(2f));

    public static final DeferredItem<?> DRONE_STATION_ITEM =
            ITEMS.registerSimpleBlockItem("drone_station", DRONE_STATION_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneStationBlockEntity>> DRONE_STATION_BE =
            BLOCK_ENTITIES.register("drone_station",
                    () -> new BlockEntityType<>(DroneStationBlockEntity::new, DRONE_STATION_BLOCK.get()));

    // Drone ranking display: read-only leaderboard viewer. Redstone signal strength selects which rank's
    // solution to run + show (0 = rank 1). Anyone may open its spectator UI.
    public static final DeferredBlock<DroneRankingBlock> DRONE_RANKING_BLOCK = BLOCKS.registerBlock(
            "drone_ranking",
            DroneRankingBlock::new,
            p -> p.noOcclusion().strength(2f));

    public static final DeferredItem<?> DRONE_RANKING_ITEM =
            ITEMS.registerSimpleBlockItem("drone_ranking", DRONE_RANKING_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneRankingBlockEntity>> DRONE_RANKING_BE =
            BLOCK_ENTITIES.register("drone_ranking",
                    () -> new BlockEntityType<>(DroneRankingBlockEntity::new, DRONE_RANKING_BLOCK.get()));

    // Creative tab (reuses the existing "itemGroup.kilagraphdemo" lang key).
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("kilagraphdemo",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kilagraphdemo"))
                    .icon(() -> new ItemStack(HOLOGRAM_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(HOLOGRAM_ITEM.get());
                        output.accept(SERVER_HOLOGRAM_ITEM.get());
                        output.accept(FERTILE_SOIL_ITEM.get());
                        output.accept(DRONE_STATION_ITEM.get());
                        output.accept(DRONE_RANKING_ITEM.get());
                    })
                    .build());

    private ModRegistries() {
    }

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
    }
}
