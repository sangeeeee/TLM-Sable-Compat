package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.TavernWork;
import com.winexp.maidtavern.maid.brewing.BrewingWork;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.winexp.maidtavern.maid.brewing.BrewingWork",remap=false)
public class TavernWorkMixin {
    @Inject(method="isCloseEnough",at=@At("HEAD"),cancellable=true)
    private void close(EntityMaid maid,CallbackInfoReturnable<Boolean> cir) {
        var work=(BrewingWork)(Object)this;
        if(TavernWork.scoped(maid,work.pos())) cir.setReturnValue(TavernWork.close(maid,work));
    }
}
