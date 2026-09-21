package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import com.winexp.maidtavern.entity.MaidTavernEntities;
import com.winexp.maidtavern.config.MaidTavernConfig;
import com.winexp.maidtavern.maid.brewing.MaidBrewingStateManager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.winexp.maidtavern.maid.brewing.common.MaidBrewingPreTickTask",remap=false)
public class TavernPreTickMixin {
    @Inject(method="validateMemories",at=@At("HEAD"))
    private void validate(EntityMaid maid,CallbackInfo ci) {
        var work=MaidBrewingStateManager.getWork(maid);
        if(work!=null && TavernWork.scoped(maid,work.pos()) && !TavernWork.allowed(maid,work)) TavernWork.clearWork(maid);
        var session=maid.getBrain().getMemory(MaidTavernEntities.BREWING_SESSION.get()).orElse(null);
        if(session!=null && session.barrelPos().isPresent() && !Work.blockAllowed(maid,session.barrelPos().get()))
            maid.getBrain().eraseMemory(MaidTavernEntities.BREWING_SESSION.get());
    }
    @Inject(method="checkWorkPathFinding",at=@At("HEAD"),cancellable=true)
    private void path(EntityMaid maid,CallbackInfo ci) {
        var work=MaidBrewingStateManager.getWork(maid);
        if(work==null || !TavernWork.scoped(maid,work.pos())) return;
        ci.cancel();
        if(!TavernWork.allowed(maid,work)) { TavernWork.clearWork(maid);return; }
        if(work.isCloseEnough(maid)) return;
        // Called at the upstream 20-tick interval. Keep a valid adjacent destination while travelling.
        var walk=maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if(walk!=null) {
            var p=walk.getTarget().currentBlockPosition();
            if(Work.blockAllowed(maid,p) && p.getCenter().distanceToSqr(work.pos().getCenter())<=16
                    && !maid.getNavigation().isDone()) return;
        }
        int attempts=maid.getBrain().getMemory(MaidTavernEntities.PATH_FINDING_ATTEMPT.get()).orElse(0)+1;
        maid.getBrain().setMemory(MaidTavernEntities.PATH_FINDING_ATTEMPT.get(),attempts);
        if(attempts>MaidTavernConfig.CONFIG.pathFindingAttempt.getAsInt() || !TavernWork.walk(maid,work)) TavernWork.clearWork(maid);
    }
}
