package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.teamtea.eclipticseasons_patch.modules.touhou_little_maid.MaidSnowyBlockMoveBehavior",remap=false)
public class SeasonSnowSearchMixin {
    @Inject(method="getWorkSearchPos",at=@At("HEAD"),cancellable=true)
    private void center(EntityMaid m,CallbackInfoReturnable<BlockPos> cir) {
        if (Work.active(m)) cir.setReturnValue(AddonWork.center(m));
    }
    @Inject(method="searchForDestination2",at=@At("HEAD"),cancellable=true)
    private void ready(ServerLevel level,EntityMaid m,CallbackInfo ci) {
        if (Work.active(m) && !Work.ready(m)) ci.cancel();
    }
    @Inject(method="checkOwnerPos",at=@At("HEAD"),cancellable=true)
    private void owner(EntityMaid m,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (Work.active(m) || !Work.blockAllowed(m,pos)) cir.setReturnValue(Work.blockAllowed(m,pos));
    }
}
