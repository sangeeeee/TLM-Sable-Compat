package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Work;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidChoppingBoardBehavior",remap=false)
public class KaleidoscopeBoardStandMixin {
    @Shadow private BlockPos currentWorkPos;
    @Shadow private BlockPos standPos;
    @Shadow private boolean hasReached;
    @Shadow @Final private float movementSpeed;
    @Inject(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",at=@At("RETURN"),cancellable=true)
    private void stand(ServerLevel level,EntityMaid m,CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !Work.active(m) || Work.reachable(m,standPos,0)) return;
        BlockPos replacement=null;
        for (Direction dir:Direction.Plane.HORIZONTAL) {
            BlockPos p=currentWorkPos.relative(dir);
            if (Work.reachable(m,p,0)) { replacement=p;break; }
        }
        if (replacement==null) {
            m.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            currentWorkPos=null;standPos=null;cir.setReturnValue(false);return;
        }
        standPos=replacement;hasReached=false;
        BehaviorUtils.setWalkAndLookTargetMemories(m,standPos,movementSpeed,0);
    }
}
