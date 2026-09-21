package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import com.winexp.maidtavern.entity.MaidTavernEntities;
import com.winexp.maidtavern.maid.brewing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.winexp.maidtavern.maid.brewing.MaidBrewingStateManager",remap=false)
public class TavernStateMixin {
    @Inject(method="startWork",at=@At("HEAD"),cancellable=true)
    private static void allowed(EntityMaid maid,BrewingWork work,CallbackInfo ci) {
        if(TavernWork.scoped(maid,work.pos()) && !TavernWork.allowed(maid,work)) {
            maid.getBrain().eraseMemory(MaidTavernEntities.BREWING_SESSION.get());
            TavernWork.clearWork(maid);ci.cancel();
        }
    }
    @Inject(method="startWork",at=@At("TAIL"))
    private static void stand(EntityMaid maid,BrewingWork work,CallbackInfo ci) {
        if(TavernWork.scoped(maid,work.pos()) && !TavernWork.walk(maid,work)) TavernWork.clearWork(maid);
    }
}
