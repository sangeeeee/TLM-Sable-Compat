package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets={"com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCookMakeTask",
        "com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCompatFarmPlantTask",
        "com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.MaidCuttingMakeTask",
        "com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.MaidSkilletMakeTask",
        "com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.MaidGrillMakeTask",
        "com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.MaidBasinMakeTask"},remap=false)
public class KitchenArrivalMixin {
    @Inject(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",at=@At("HEAD"),cancellable=true)
    private void approaching(ServerLevel level,EntityMaid m,CallbackInfoReturnable<Boolean> cir) {
        // CookMove now walks to a standing point next to the workstation. Keep the work
        // address while travelling instead of requiring WALK_TARGET == TARGET_POS.
        double range=m.getTask() instanceof com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask<?,?> cook ? cook.getCloseEnoughDist()
                : m.getTask() instanceof com.github.wallev.maidsoulkitchen.api.task.v1.farm.ICompatFarm<?,?> farm ? farm.getCloseEnoughDist() : 0;
        var work=m.getBrain().getMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
        if (work.isPresent() && AddonWork.board(m,work.get().currentBlockPosition())) {
            cir.setReturnValue(AddonWork.targetInteractable(m,range));return;
        }
        if (Work.active(m) && range>0 && m.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)) {
            var target=m.getBrain().getMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
            if(target.isPresent() && Work.blockAllowed(m,target.get().currentBlockPosition())
                    && !AddonWork.targetInteractable(m,range)) cir.setReturnValue(false);
        }
    }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double distance(EntityMaid m,Vec3 target) {
        return Work.active(m) && !AddonWork.visible(m,net.minecraft.core.BlockPos.containing(target)) ? Double.POSITIVE_INFINITY : Work.distance(m,target);
    }
    @Inject(method="start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void start(ServerLevel level,EntityMaid m,long time,CallbackInfo ci) {
        var target=m.getBrain().getMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
        if (!AddonWork.targetValid(m) || target.isPresent() &&
                (AddonWork.board(m,target.get().currentBlockPosition()) ? !AddonWork.targetInteractable(m,1)
                        : Work.active(m) && !AddonWork.visible(m,target.get().currentBlockPosition()))) ci.cancel();
    }
}
