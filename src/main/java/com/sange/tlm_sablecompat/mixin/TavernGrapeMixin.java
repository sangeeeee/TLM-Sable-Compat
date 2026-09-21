package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import com.winexp.maidtavern.maid.grape.TaskGrape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.winexp.maidtavern.maid.grape.TaskGrape",remap=false)
public class TavernGrapeMixin {
    @Inject(method="getGrapePos",at=@At("RETURN"),cancellable=true)
    private void grape(EntityMaid maid,BlockPos base,CallbackInfoReturnable<BlockPos> cir) {
        if(cir.getReturnValue()!=null && !Work.blockAllowed(maid,cir.getReturnValue())) cir.setReturnValue(null);
    }
    @Inject(method="harvest",at=@At("HEAD"),cancellable=true)
    private void harvest(EntityMaid maid,BlockPos crop,BlockState state,CallbackInfo ci) {
        var task=(TaskGrape)(Object)this;
        BlockPos grape=task.getGrapePos(maid,crop.below());
        if(grape==null || TavernWork.scoped(maid,grape) &&
                (!AddonWork.visible(maid,grape) || Work.distance(maid,crop.below().getCenter())>Math.pow(task.getCloseEnoughDist(),2))) ci.cancel();
    }
}
