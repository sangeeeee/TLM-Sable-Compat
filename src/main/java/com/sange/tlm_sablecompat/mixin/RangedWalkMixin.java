package com.sange.tlm_sablecompat.mixin;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=MaidRangedWalkToTarget.class,remap=false)
public class RangedWalkMixin {
    @Inject(method="shouldEraseWalkTarget",at=@At("HEAD"),cancellable=true)
    private static void range(EntityMaid m,LivingEntity target,CallbackInfoReturnable<Boolean> cir) {
        if (Work.active(m)) { var plan=Combat.plan(m,target); cir.setReturnValue(!plan.allowed() || plan.destination()==null); }
    }
}
