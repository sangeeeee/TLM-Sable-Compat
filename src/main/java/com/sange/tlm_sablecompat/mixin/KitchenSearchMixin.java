package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets={"com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCookMoveTask",
        "com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCompatFruitMoveTask"},remap=false)
public class KitchenSearchMixin {
    @Inject(method="getSearchPos",at=@At("HEAD"),cancellable=true)
    private static void center(EntityMaid m,CallbackInfoReturnable<BlockPos> cir) {
        if (Work.active(m)) cir.setReturnValue(m.hasRestriction()?m.getRestrictCenter():AddonWork.center(m).below());
    }
    @Inject(method="searchForDestination",at=@At("HEAD"),cancellable=true)
    private void ready(ServerLevel level,EntityMaid m,CallbackInfo ci) {
        if (Work.active(m) && !Work.ready(m)) ci.cancel();
    }
    @Inject(method="checkOwnerPos",at=@At("HEAD"),cancellable=true)
    private void owner(EntityMaid m,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (Work.active(m) || !Work.blockAllowed(m,pos)) cir.setReturnValue(Work.blockAllowed(m,pos));
    }
    @Inject(method="shouldMoveTo",at=@At("HEAD"),cancellable=true)
    private void candidate(ServerLevel level,EntityMaid m,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (!Work.blockAllowed(m,pos)) cir.setReturnValue(false);
    }
}
