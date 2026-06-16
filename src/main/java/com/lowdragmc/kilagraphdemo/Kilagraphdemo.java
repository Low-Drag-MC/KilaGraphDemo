package com.lowdragmc.kilagraphdemo;

import com.lowdragmc.kilagraphdemo.network.ModNetworking;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Kilagraphdemo.MODID)
public class Kilagraphdemo {
    public static final String MODID = "kilagraphdemo";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Kilagraphdemo(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.init(modEventBus);
        modEventBus.addListener(ModNetworking::register);
    }
}
