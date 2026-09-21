package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidJoyTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaidJoyTask.class,remap=false)
public class JoyTaskMixin {
    @Shadow @Final private int closeEnoughDist;
    @Redirect(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"))
    private double arrival(BlockPos pos,Position point,ServerLevel level,EntityMaid m) {
        return Work.active(m) && !AddonWork.canInteract(m,pos,closeEnoughDist) ? Double.POSITIVE_INFINITY : pos.distToCenterSqr(point);
    }
    @Redirect(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ai/behavior/BehaviorUtils;setWalkAndLookTargetMemories(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;FI)V"))
    private void approach(LivingEntity entity,BlockPos pos,float speed,int tolerance) {
        EntityMaid m=(EntityMaid)entity;
        if (!Work.active(m)) { BehaviorUtils.setWalkAndLookTargetMemories(m,pos,speed,tolerance);return; }
        var plan=AddonWork.approach(m,pos,closeEnoughDist);
        if (!plan.allowed()) return;
        if (plan.standing()!=null) m.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(plan.standing(),speed,0));
        m.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,new BlockPosTracker(pos));
    }
    @Inject(method="start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void validate(ServerLevel level,EntityMaid m,long time,CallbackInfo ci) {
        if (Work.active(m) && !AddonWork.targetInteractable(m,closeEnoughDist)) {
            m.getBrain().eraseMemory(InitEntities.TARGET_POS.get());ci.cancel();
        }
    }
}
