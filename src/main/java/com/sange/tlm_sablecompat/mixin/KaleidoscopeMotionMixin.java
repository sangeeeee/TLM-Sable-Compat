package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.AddonWork;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets={"com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidMillstoneBehavior",
        "com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidPressingTubBehavior"},remap=false)
public class KaleidoscopeMotionMixin {
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;getX()D"))
    private double x(EntityMaid m) { return AddonWork.position(m).x; }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;getZ()D"))
    private double z(EntityMaid m) { return AddonWork.position(m).z; }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;setDeltaMovement(DDD)V"))
    private void motion(EntityMaid m,double x,double y,double z) { AddonWork.localMotion(m,x,y,z); }
}
