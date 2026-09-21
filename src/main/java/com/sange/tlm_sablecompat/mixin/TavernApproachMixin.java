package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.*;
import com.sange.tlm_sablecompat.*;
import com.winexp.maidtavern.maid.brewing.barrel.MaidBrewingMoveToBarrelTask;
import com.winexp.maidtavern.maid.brewing.storage.MaidBrewingMoveToStorageTask;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.winexp.maidtavern.maid.task.MaidSurroundingMoveTask",remap=false)
public class TavernApproachMixin implements AddonWork.BerryPath {
    @Unique private BlockPos tlmsc$standing;
    @Override public BlockPos tlmsc$berryStanding() { return tlmsc$standing; }

    @Inject(method="checkPathReach",at=@At("HEAD"),cancellable=true)
    private void approach(EntityMaid maid,MaidPathFindingBFS bfs,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (!TavernWork.scoped(maid,pos)) return;
        double reach=(Object)this instanceof MaidBrewingMoveToBarrelTask ? 2.5
                : (Object)this instanceof MaidBrewingMoveToStorageTask ? 3 : 2;
        var approach=TavernWork.approach(maid,pos,reach,bfs::canPathReach);
        tlmsc$standing=approach.standing();
        cir.setReturnValue(approach.allowed());
    }
}
