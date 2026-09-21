package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Spaces;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidMoveToBlockTask.class, remap = false)
public class WorkSearchMixin {
    @Inject(method = "getWorkSearchPos", at = @At("RETURN"), cancellable = true)
    private void center(EntityMaid maid, CallbackInfoReturnable<BlockPos> cir) {
        if (!maid.hasRestriction() && Spaces.tracking(maid) != null) cir.setReturnValue(Spaces.localPosition(maid));
    }
    @Inject(method = "checkOwnerPos", at = @At("HEAD"), cancellable = true)
    private void owner(EntityMaid maid, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Spaces.tracking(maid) == null) return;
        cir.setReturnValue(Spaces.belongs(maid.level(), pos, Spaces.tracking(maid)) && (maid.isHomeModeEnable() ||
                maid.getOwner() != null && Spaces.project(maid.level(), pos.getCenter()).distanceToSqr(maid.getOwner().position()) < 64));
    }
}
