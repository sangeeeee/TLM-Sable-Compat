package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.sange.tlm_sablecompat.*;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets={"com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.MaidCuttingMakeTask",
        "com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.MaidSkilletMakeTask",
        "com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.MaidGrillMakeTask",
        "com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.MaidBasinMakeTask"},remap=false)
public class KitchenContinuousMixin {
    @Unique private boolean tlmsc$valid(EntityMaid m) {
        double range=m.getTask() instanceof com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask<?,?> task ? task.getCloseEnoughDist() : 3.2;
        return AddonWork.targetInteractable(m,range);
    }
    @Unique private boolean tlmsc$checked(EntityMaid m) {
        return Work.active(m) || m.getBrain().getMemory(InitEntities.TARGET_POS.get())
                .map(t->AddonWork.board(m,t.currentBlockPosition())).orElse(false);
    }
    @Inject(method="canStillUse(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)Z",at=@At("HEAD"),cancellable=true)
    private void use(ServerLevel level,EntityMaid m,long time,CallbackInfoReturnable<Boolean> cir) {
        if (!AddonWork.targetValid(m) || tlmsc$checked(m) && !tlmsc$valid(m)) cir.setReturnValue(false);
    }
    @Inject(method="tick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void tick(ServerLevel level,EntityMaid m,long time,CallbackInfo ci) {
        if (!AddonWork.targetValid(m) || tlmsc$checked(m) && !tlmsc$valid(m)) ci.cancel();
    }
}
