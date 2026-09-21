package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IChoppingBoard;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidChoppingBoardBehavior",remap=false)
public abstract class KaleidoscopeBoardStandMixin {
    @Shadow private BlockPos currentWorkPos;
    @Shadow private BlockPos standPos;
    @Shadow private boolean hasReached;
    @Shadow private int actionStage;
    @Shadow private int failCount;
    @Shadow @Final private float movementSpeed;
    @Shadow private void tryPutItem(ServerLevel level,EntityMaid m,IChoppingBoard board) { throw new AssertionError(); }
    @Shadow private void tryCut(ServerLevel level,EntityMaid m,IChoppingBoard board) { throw new AssertionError(); }
    @Shadow private void tryTakeOut(ServerLevel level,EntityMaid m,IChoppingBoard board) { throw new AssertionError(); }

    @Redirect(method="findChoppingBoard",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/MaidPathFindingBFS;canPathReach(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean approach(MaidPathFindingBFS bfs,BlockPos pos,ServerLevel level,EntityMaid m) {
        // Reuse the prepared evaluator; never start a nested navigation search here.
        return AddonWork.approach(m,pos,1,bfs::canPathReach).allowed();
    }
    @Inject(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",at=@At("RETURN"),cancellable=true)
    private void stand(ServerLevel level,EntityMaid m,CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        var plan=AddonWork.approach(m,currentWorkPos,1);
        if (!plan.allowed()) {
            m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            currentWorkPos=null;standPos=null;cir.setReturnValue(false);return;
        }
        standPos=plan.standing()==null ? Spaces.localPosition(m) : plan.standing();
        hasReached=plan.standing()==null;
        if (hasReached) m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        else BehaviorUtils.setWalkAndLookTargetMemories(m,standPos,movementSpeed,0);
    }
    @Inject(method="tick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void work(ServerLevel level,EntityMaid m,long time,CallbackInfo ci) {
        ci.cancel();
        if (currentWorkPos==null || !Work.blockAllowed(m,currentWorkPos)) return;
        if (!AddonWork.canInteract(m,currentWorkPos,1)) {
            hasReached=false;
            // Existing walk target handles travel; only re-plan at a bounded cadence if blocked/moved.
            if (m.tickCount%20==0) {
                var plan=AddonWork.approach(m,currentWorkPos,1);
                if (plan.allowed() && plan.standing()!=null) {
                    standPos=plan.standing();BehaviorUtils.setWalkAndLookTargetMemories(m,standPos,movementSpeed,0);
                }
            }
            return;
        }
        hasReached=true;m.getNavigation().stop();m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (!(level.getBlockEntity(currentWorkPos) instanceof IChoppingBoard board)) return;
        m.getLookControl().setLookAt(currentWorkPos.getCenter());
        // start() resets the task, not the board. A partly cut ingredient cannot be
        // replaced or taken out; resume the normal knife/timer-controlled cutting stage.
        if (actionStage == 0 && board instanceof ChoppingBoardBlockEntity chopping
                && !chopping.getCurrentCutStack().isEmpty()
                && chopping.getCurrentCutCount() < chopping.getMaxCutCount()) {
            actionStage = 1;
            failCount = 0;
        }
        switch(actionStage) {
            case 0 -> tryPutItem(level,m,board);
            case 1 -> tryCut(level,m,board);
            case 2 -> tryTakeOut(level,m,board);
        }
    }
}
