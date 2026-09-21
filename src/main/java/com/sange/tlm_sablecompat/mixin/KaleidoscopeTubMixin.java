package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.bmt.kaleidoscope_compat.compat.touhoulittlemaid.MaidPressingTubBehavior",remap=false)
public class KaleidoscopeTubMixin {
    @Shadow private BlockPos currentWorkPos;
    @Shadow @Final private float movementSpeed;
    @Inject(method="tick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void arrive(ServerLevel level,EntityMaid m,long time,CallbackInfo ci) {
        if (!Work.active(m) || currentWorkPos==null) return;
        if (Work.distance(m,currentWorkPos.getCenter())>2.25) {
            if (Work.blockAllowed(m,currentWorkPos)) BehaviorUtils.setWalkAndLookTargetMemories(m,currentWorkPos,movementSpeed,0);
            ci.cancel();
        }
    }
}
