package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets={"com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidChoppingBoardBehavior",
        "com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidMillstoneBehavior",
        "com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidPressingTubBehavior"},remap=false)
public class KaleidoscopeTaskMixin {
    @Shadow(remap=false) private BlockPos currentWorkPos;

    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos center(EntityMaid m) { return BlockPos.containing(AddonWork.position(m)); }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean candidate(EntityMaid m,BlockPos pos) { return Work.blockAllowed(m,pos) && m.isWithinRestriction(pos); }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 position(EntityMaid m) { return AddonWork.position(m); }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;getY()D"))
    private double y(EntityMaid m) { return AddonWork.position(m).y; }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;distanceToSqr(DDD)D"))
    private double distance(EntityMaid m,double x,double y,double z) { return AddonWork.position(m).distanceToSqr(x,y,z); }

    @Inject(method="canStillUse(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)Z",at=@At("HEAD"),cancellable=true)
    private void use(ServerLevel level,EntityMaid m,long time,CallbackInfoReturnable<Boolean> cir) {
        if (currentWorkPos!=null && !Work.blockAllowed(m,currentWorkPos)) cir.setReturnValue(false);
        // The original close-distance condition aborts the task before the maid finishes walking.
        else if (Work.active(m) || (Object)this instanceof com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidChoppingBoardBehavior)
            cir.setReturnValue(currentWorkPos!=null && level.getBlockEntity(currentWorkPos)!=null);
    }
    @Inject(method="tick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void tick(ServerLevel level,EntityMaid m,long time,CallbackInfo ci) {
        if (currentWorkPos==null || !Work.blockAllowed(m,currentWorkPos)
                || Work.active(m) && !AddonWork.board(m,currentWorkPos) && !AddonWork.visible(m,currentWorkPos)) ci.cancel();
    }
}
