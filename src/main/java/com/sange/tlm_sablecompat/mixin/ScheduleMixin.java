package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.*;
import com.sange.tlm_sablecompat.Homes;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchedulePos.class, remap = false)
public class ScheduleMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tick(EntityMaid maid, CallbackInfo ci) { if (Homes.tick(maid)) ci.cancel(); }
    @Inject(method = "setHomeModeEnable", at = @At("RETURN"))
    private void rememberWorldHome(EntityMaid maid, BlockPos pos, CallbackInfo ci) {
        if (!((SchedulePos)(Object)this).isConfigured() && !maid.level().isClientSide()) Homes.setSimple(maid);
    }
}
