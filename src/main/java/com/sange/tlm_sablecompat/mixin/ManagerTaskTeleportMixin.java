package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.hmtl.tlmmaidmanager.world.WorkBlockData;
import com.sange.tlm_sablecompat.ManagerAnchors;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.hmtl.tlmmaidmanager.network.MaidTeleporter", remap=false)
public class ManagerTaskTeleportMixin {
    @Inject(method="teleportMaidToWorkBlockForTask", at=@At("HEAD"), cancellable=true)
    private static void anchor(EntityMaid maid, WorkBlockData.Entry entry, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ManagerAnchors.teleportForTask(maid, entry));
    }
}
