package com.sange.tlm_sablecompat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point for the Touhou Little Maid and Sable compatibility mod.
 */
@Mod(TLMSableCompat.MOD_ID)
public final class TLMSableCompat {
    public static final String MOD_ID = "tlm_sablecompat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TLMSableCompat(net.neoforged.bus.api.IEventBus modBus) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::afterServerTick);
        if (Boolean.getBoolean("tlm_sablecompat.tests")) {
            modBus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) ->
                    event.register(com.sange.tlm_sablecompat.test.CompatGameTests.class));
        }
        LOGGER.info("TLM Sable Compat initialized");
    }

    private void afterServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        BindingStore.get(event.getServer().overworld()).settle();
    }
}
