package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerVehicleTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Spaces;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidFollowOwnerVehicleTask.class, remap = false)
public class VehicleFollowMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z", at = @At("HEAD"), cancellable = true)
    private void preventCrossFrameVehiclePath(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (maid.getOwner() != null && Spaces.tracking(maid) != Spaces.tracking(maid.getOwner()) &&
                (Spaces.tracking(maid) != null || Spaces.tracking(maid.getOwner()) != null)) cir.setReturnValue(false);
    }
}
