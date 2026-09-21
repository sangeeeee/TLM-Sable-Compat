package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import com.winexp.maidtavern.entity.MaidTavernEntities;
import com.winexp.maidtavern.maid.brewing.MaidBrewingStateManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.winexp.maidtavern.maid.brewing.barrel.MaidBrewingAddIngredientsTask",remap=false)
public class TavernIngredientsMixin {
    @Inject(method="canStillUse(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)Z",at=@At("HEAD"),cancellable=true)
    private void continueWork(ServerLevel level,EntityMaid maid,long time,CallbackInfoReturnable<Boolean> cir) {
        var work=MaidBrewingStateManager.getWork(maid);
        if(work==null || TavernWork.scoped(maid,work.pos()) && !work.isCloseEnough(maid)) cir.setReturnValue(false);
    }
    @Inject(method="tick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void tick(ServerLevel level,EntityMaid maid,long time,CallbackInfo ci) {
        var work=MaidBrewingStateManager.getWork(maid);
        if(work==null || TavernWork.scoped(maid,work.pos()) && !work.isCloseEnough(maid)) ci.cancel();
    }
    @Inject(method="stop(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V",at=@At("HEAD"),cancellable=true)
    private void stop(EntityMaid maid,CallbackInfo ci) {
        var work=MaidBrewingStateManager.getWork(maid);
        if(work==null) { ci.cancel();return; }
        if(!TavernWork.scoped(maid,work.pos())) return;
        // Re-approach a partially filled barrel without restarting its fluid/ingredient stages.
        var session=maid.getBrain().getMemory(MaidTavernEntities.BREWING_SESSION.get()).orElse(null);
        TavernWork.clearWork(maid);
        if(session!=null && (session.barrelPos().isEmpty() || !Work.blockAllowed(maid,session.barrelPos().get())))
            maid.getBrain().eraseMemory(MaidTavernEntities.BREWING_SESSION.get());
        ci.cancel();
    }
}
