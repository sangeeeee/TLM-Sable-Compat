package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.sange.tlm_sablecompat.Work;
import com.sange.tlm_sablecompat.AddonWork;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCookMoveTask",remap=false)
public class KitchenCookSearchMixin {
    @Shadow @Final private ICookTask<?,?> task;
    @Inject(method="shouldMoveTo",at=@At("HEAD"),cancellable=true)
    private void reachable(ServerLevel level,EntityMaid m,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        // Only pathfind for block entities, before the recipe manager takes ingredients from containers.
        if ((Work.active(m) || AddonWork.board(m,pos)) && (level.getBlockEntity(pos)==null || !task.isCookBE(level.getBlockEntity(pos))
                || !AddonWork.approach(m,pos,task.getCloseEnoughDist()).allowed())) cir.setReturnValue(false);
    }
    @Inject(method="setWalkAndLookTargetMemories",at=@At("HEAD"),cancellable=true)
    private static void walk(LivingEntity entity,BlockPos walk,BlockPos look,float speed,int distance,CallbackInfo ci) {
        if (!(entity instanceof EntityMaid m) || !Work.active(m) && !AddonWork.board(m,look)) return;
        double reach=m.getTask() instanceof ICookTask<?,?> task ? task.getCloseEnoughDist() : 3.2;
        var approach=AddonWork.approach(m,look,reach);
        if(!approach.allowed()) { ci.cancel();return; }
        if(approach.standing()!=null) m.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(approach.standing(),speed,0));
        else m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        m.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,new BlockPosTracker(look));
        m.getBrain().setMemory(InitEntities.TARGET_POS.get(),new BlockPosTracker(look));
        m.getBrain().setMemory(MkMemories.DESTROY_POS.get(),new BlockPosTracker(look));
        ci.cancel();
    }
}
