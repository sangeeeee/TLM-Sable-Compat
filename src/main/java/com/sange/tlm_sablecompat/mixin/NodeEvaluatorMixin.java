package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidNodeEvaluator.class, remap = false)
public abstract class NodeEvaluatorMixin extends WalkNodeEvaluator {
    @Inject(method = "getPathType", at = @At("HEAD"), cancellable = true)
    private void limit(PathfindingContext context, int x, int y, int z, CallbackInfoReturnable<PathType> cir) {
        if (!(mob instanceof EntityMaid maid)) return;
        var s = Spaces.tracking(maid);
        if (Work.active(maid) && !Work.ready(maid)) { cir.setReturnValue(PathType.BLOCKED); return; }
        if (s != null && (!Work.ready(maid) || !Spaces.belongs(maid.level(), new BlockPos(x, y, z), s) || !Spaces.upright(s))) cir.setReturnValue(PathType.BLOCKED);
    }
}
