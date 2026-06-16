package com.lowdragmc.kilagraphdemo;

import com.lowdragmc.kilagraphdemo.block.HologramBlock;
import com.lowdragmc.kilagraphdemo.block.HologramBlockEntity;
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

    // Creative tab (reuses the existing "itemGroup.kilagraphdemo" lang key).
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("kilagraphdemo",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kilagraphdemo"))
                    .icon(() -> new ItemStack(HOLOGRAM_ITEM.get()))
                    .displayItems((params, output) -> output.accept(HOLOGRAM_ITEM.get()))
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
