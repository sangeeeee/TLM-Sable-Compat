package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MoveToTargetSink.class)
public class MoveSinkMixin {
    @Redirect(method = "tryComputePath", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private net.minecraft.world.phys.Vec3 fallbackDirection(net.minecraft.core.Vec3i position, Mob mob, WalkTarget target, long time) {
        var point = net.minecraft.world.phys.Vec3.atBottomCenterOf(position);
        return mob instanceof EntityMaid ? Spaces.project(mob.level(), point) : point;
    }
    @Inject(method = "reachedTarget", at = @At("HEAD"), cancellable = true)
    private void reached(Mob mob, WalkTarget target, CallbackInfoReturnable<Boolean> cir) {
        if (!(mob instanceof EntityMaid)) return;
        var s = Sable.HELPER.getContaining(mob.level(), target.getTarget().currentBlockPosition());
        if (s != null) {
            cir.setReturnValue(!s.isRemoved() && Spaces.tracking(mob) == s && target.getTarget().currentBlockPosition()
                    .distManhattan(BlockPos.containing(Spaces.local(s, mob.position()))) <= target.getCloseEnoughDist());
        }
    }
}
