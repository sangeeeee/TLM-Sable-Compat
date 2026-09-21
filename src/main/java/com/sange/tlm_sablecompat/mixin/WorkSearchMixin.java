package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Spaces;
import com.sange.tlm_sablecompat.Work;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidMoveToBlockTask.class, remap = false)
public class WorkSearchMixin {
    @Redirect(method="searchForDestination",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ai/behavior/BehaviorUtils;setWalkAndLookTargetMemories(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;FI)V"))
    private void berryStanding(net.minecraft.world.entity.LivingEntity entity,BlockPos pos,float speed,int distance) {
        if (entity instanceof EntityMaid m && Work.active(m)
                && (Object)this instanceof com.sange.tlm_sablecompat.AddonWork.BerryPath berry) {
            var standing=berry.tlmsc$berryStanding();
            m.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,new net.minecraft.world.entity.ai.behavior.BlockPosTracker(pos));
            if(standing!=null) m.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,new net.minecraft.world.entity.ai.memory.WalkTarget(standing,speed,0));
            else m.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        } else net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(entity,pos,speed,distance);
    }
    @Inject(method="searchForDestination",at=@At("HEAD"),cancellable=true)
    private void allowed(net.minecraft.server.level.ServerLevel level,EntityMaid maid,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (Work.active(maid) && !Work.ready(maid)) ci.cancel();
    }
    @Inject(method = "getWorkSearchPos", at = @At("RETURN"), cancellable = true)
    private void center(EntityMaid maid, CallbackInfoReturnable<BlockPos> cir) {
        if (!maid.hasRestriction() && Spaces.tracking(maid) != null) cir.setReturnValue(Spaces.localPosition(maid));
    }
    @Inject(method = "checkOwnerPos", at = @At("HEAD"), cancellable = true)
    private void owner(EntityMaid maid, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Work.active(maid) || Sable.HELPER.getContaining(maid.level(),pos)!=null) cir.setReturnValue(Work.blockAllowed(maid,pos));
    }
}
