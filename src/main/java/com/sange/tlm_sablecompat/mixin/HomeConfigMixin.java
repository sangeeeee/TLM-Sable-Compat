package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.network.message.MaidConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidConfigPackage.class, remap = false)
public class HomeConfigMixin {
    @Inject(method = "handleHome", at = @At("HEAD"), cancellable = true)
    private static void home(MaidConfigPackage message, ServerPlayer player, EntityMaid maid, CallbackInfo ci) {
        if (!message.home()) return;
        boolean configured = maid.getSchedulePos().isConfigured();
        if (!configured && Spaces.tracking(maid) == null && !Homes.managed(maid)) return;
        if (configured && !Homes.managed(maid) && Spaces.tracking(maid) == null) return;
        ci.cancel();
        if (configured) {
            String error = Homes.validateMaid(maid, Homes.binding(maid));
            if (!error.isEmpty()) { Homes.tell(player, error); return; }
        } else if (!Homes.setSimple(maid)) return;
        Homes.sync(maid, Homes.binding(maid));
        maid.setHomeModeEnable(true);
        maid.getSchedulePos().restrictTo(maid);
        Navigation.clearMovement(maid);
    }
}
