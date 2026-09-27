package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.*;
import com.sange.tlm_sablecompat.Homes;
import com.sange.tlm_sablecompat.Spaces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SchedulePos.class, remap = false)
public class ScheduleMixin {
    @Inject(method = "getNearestPos", at = @At("HEAD"), cancellable = true)
    private void nearestWorldPosition(EntityMaid maid, CallbackInfoReturnable<BlockPos> cir) {
        if (!(maid.level() instanceof ServerLevel level) || !((SchedulePos)(Object)this).isConfigured()
                || !Homes.managed(maid)) return;
        var binding = Homes.binding(maid);
        if (!Homes.status(level, binding).isEmpty()) return;
        // Callers use this query for distance to maid.blockPosition(), including Maid
        // Manager's copied Home handler. Return a world-space result, without changing
        // the local work/idle/sleep addresses needed by navigation and commuting.
        BlockPos nearest = null;
        double distance = Double.MAX_VALUE;
        for (var point : binding.points) {
            var structure = Spaces.resolve(level, point.structure());
            BlockPos world = BlockPos.containing(Spaces.world(structure, point.position().getCenter()));
            double candidate = world.distSqr(maid.blockPosition());
            if (candidate < distance) { nearest = world; distance = candidate; }
        }
        cir.setReturnValue(nearest);
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tick(EntityMaid maid, CallbackInfo ci) { if (Homes.tick(maid)) ci.cancel(); }
    @Inject(method = "setHomeModeEnable", at = @At("RETURN"))
    private void rememberWorldHome(EntityMaid maid, BlockPos pos, CallbackInfo ci) {
        if (!((SchedulePos)(Object)this).isConfigured() && !maid.level().isClientSide()) Homes.setSimple(maid);
    }
}
