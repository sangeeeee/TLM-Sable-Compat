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
    @org.spongepowered.asm.mixin.Shadow private net.minecraft.world.level.pathfinder.Path path;
    @org.spongepowered.asm.mixin.Shadow private float speedModifier;
    @Inject(method="tryComputePath",at=@At("HEAD"),cancellable=true)
    private void taskPath(Mob mob,WalkTarget target,long time,CallbackInfoReturnable<Boolean> cir) {
        if (!(mob instanceof EntityMaid m) || !Work.active(m)) return;
        if (!Work.ready(m)) { cir.setReturnValue(false); return; }
        if (target.getTarget() instanceof net.minecraft.world.entity.ai.behavior.EntityTracker tracker) {
            var entity=tracker.getEntity();
            net.minecraft.core.BlockPos pos;
            if (entity instanceof net.minecraft.world.entity.LivingEntity living && m.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null)==entity) {
                Combat.Plan plan=Combat.plan(m,living);
                if (!plan.allowed() || plan.destination()==null) { cir.setReturnValue(false); return; }
                pos=plan.destination();
            } else {
                if (entity!=m.getOwner() && !Work.entityReachable(m,entity)) { cir.setReturnValue(false); return; }
                if (Spaces.tracking(entity)!=Work.space(m)) { cir.setReturnValue(false); return; }
                pos=net.minecraft.core.BlockPos.containing(Spaces.local(Work.space(m),entity.position()));
            }
            path=m.getNavigation().createPath(pos,Math.max(0,target.getCloseEnoughDist()));
            speedModifier=target.getSpeedModifier();
            cir.setReturnValue(path!=null && path.canReach());
        } else {
            var pos=target.getTarget().currentBlockPosition();
            var containing=Sable.HELPER.getContaining(m.level(),pos);
            if (containing!=null && containing!=Work.space(m)) cir.setReturnValue(false);
        }
    }
    @Redirect(method = "tryComputePath", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private net.minecraft.world.phys.Vec3 fallbackDirection(net.minecraft.core.Vec3i position, Mob mob, WalkTarget target, long time) {
        var point = net.minecraft.world.phys.Vec3.atBottomCenterOf(position);
        return mob instanceof EntityMaid ? Spaces.project(mob.level(), point) : point;
    }
    @Inject(method = "reachedTarget", at = @At("HEAD"), cancellable = true)
    private void reached(Mob mob, WalkTarget target, CallbackInfoReturnable<Boolean> cir) {
        if (!(mob instanceof EntityMaid)) return;
        if (target.getTarget() instanceof net.minecraft.world.entity.ai.behavior.EntityTracker tracker && mob instanceof EntityMaid m && Work.active(m)) {
            var enemy=m.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (enemy==tracker.getEntity() && Combat.canHitHere(m,enemy)) { cir.setReturnValue(true); return; }
        }
        var s = Sable.HELPER.getContaining(mob.level(), target.getTarget().currentBlockPosition());
        if (s != null) {
            cir.setReturnValue(!s.isRemoved() && Spaces.tracking(mob) == s && target.getTarget().currentBlockPosition()
                    .distManhattan(BlockPos.containing(Spaces.local(s, mob.position()))) <= target.getCloseEnoughDist());
        }
    }
}
