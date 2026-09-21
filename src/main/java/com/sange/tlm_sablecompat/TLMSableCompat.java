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

    public TLMSableCompat() {
        LOGGER.info("TLM Sable Compat initialized");
    }
}
