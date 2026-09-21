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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::entityRemoved);
        if (Boolean.getBoolean("tlm_sablecompat.tests")) {
            modBus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) -> {
                event.register(com.sange.tlm_sablecompat.test.CompatGameTests.class);
                event.register(com.sange.tlm_sablecompat.test.WorkGameTests.class);
                event.register(com.sange.tlm_sablecompat.test.FishingGameTests.class);
                event.register(com.sange.tlm_sablecompat.test.PickupGameTests.class);
                event.register(com.sange.tlm_sablecompat.test.JoyGameTests.class);
                event.register(com.sange.tlm_sablecompat.test.NavigationGameTests.class);
                if (net.neoforged.fml.ModList.get().isLoaded("maidtavern"))
                    event.register(com.sange.tlm_sablecompat.test.TavernGameTests.class);
                if (net.neoforged.fml.ModList.get().isLoaded("muhc"))
                    event.register(com.sange.tlm_sablecompat.test.HandCrankGameTests.class);
                if (net.neoforged.fml.ModList.get().isLoaded("kaleidoscope_compat")
                        && net.neoforged.fml.ModList.get().isLoaded("maidsoulkitchen")
                        && net.neoforged.fml.ModList.get().isLoaded("eclipticseasons_multimodpatch"))
                    event.register(com.sange.tlm_sablecompat.test.AddonGameTests.class);
            });
        }
        LOGGER.info("TLM Sable Compat initialized");
    }

    private void afterServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        BindingStore.get(event.getServer().overworld()).settle();
    }
    private void entityRemoved(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        var entity = event.getEntity();
        if (!event.getLevel().isClientSide() && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy())
            Resting.detach(entity);
    }
}
