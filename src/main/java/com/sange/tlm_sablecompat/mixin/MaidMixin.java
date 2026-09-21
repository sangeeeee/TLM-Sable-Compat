package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = EntityMaid.class, remap = false)
public class MaidMixin {
    @Inject(method = "isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void restriction(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid m = (EntityMaid)(Object)this;
        if (!m.level().isClientSide() && m.isHomeModeEnable() && Homes.managed(m)) cir.setReturnValue(Homes.within(m, pos));
    }
    @Inject(method = "getBrainSearchPos", at = @At("RETURN"), cancellable = true)
    private void searchCenter(CallbackInfoReturnable<BlockPos> cir) {
        EntityMaid m = (EntityMaid)(Object)this;
        if (!m.hasRestriction() && Spaces.tracking(m) != null) cir.setReturnValue(Spaces.localPosition(m));
    }
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void suspend(CallbackInfo ci) {
        EntityMaid m = (EntityMaid)(Object)this;
        Navigation.refresh(m);
        if (!m.isHomeModeEnable()) Navigation.follow(m);
        if (Homes.suspended(m) || Homes.away(m)) { Navigation.clearMovement(m); Homes.tick(m); ci.cancel(); }
        else if (!Spaces.upright(Spaces.tracking(m))) { Navigation.clearMovement(m); ci.cancel(); }
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void attachments(CallbackInfo ci) { Resting.tick((EntityMaid)(Object)this); }
    @Inject(method="searchDimension",at=@At("RETURN"),cancellable=true)
    private void entitySearch(CallbackInfoReturnable<net.minecraft.world.phys.AABB> cir) {
        EntityMaid m=(EntityMaid)(Object)this;
        if (!Work.active(m)) return;
        var box=cir.getReturnValue();
        var s=dev.ryanhcode.sable.Sable.HELPER.getContaining(m.level(),BlockPos.containing(box.getCenter()));
        if (s!=null) cir.setReturnValue(Work.worldBox(s,box));
    }
    @Inject(method="canPathReach(Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void entityReach(net.minecraft.world.entity.Entity target,CallbackInfoReturnable<Boolean> cir) {
        EntityMaid m=(EntityMaid)(Object)this;
        if (Work.active(m) || Spaces.tracking(target)!=null) cir.setReturnValue(Work.entityReachable(m,target));
    }
    @Inject(method="canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z",at=@At("RETURN"),cancellable=true)
    private void attackPlan(LivingEntity target,CallbackInfoReturnable<Boolean> cir) {
        EntityMaid m=(EntityMaid)(Object)this;
        if (cir.getReturnValueZ() && Work.active(m)) cir.setReturnValue(Combat.plan(m,target).allowed());
    }
    @Inject(method="performRangedAttack",at=@At("HEAD"),cancellable=true)
    private void range(LivingEntity target,float factor,CallbackInfo ci) {
        EntityMaid m=(EntityMaid)(Object)this;
        if (Work.active(m) && !Combat.canHitHere(m,target)) ci.cancel();
    }
    @Inject(method="doHurtTarget",at=@At("HEAD"),cancellable=true)
    private void melee(net.minecraft.world.entity.Entity target,CallbackInfoReturnable<Boolean> cir) {
        EntityMaid m=(EntityMaid)(Object)this;
        if (Work.active(m) && (!Work.ready(m) || !m.hasLineOfSight(target))) cir.setReturnValue(false);
    }
    @Inject(method="startSleeping",at=@At("HEAD"))
    private void bedAnchor(BlockPos pos,CallbackInfo ci) { Resting.attachSleep((EntityMaid)(Object)this,pos); }
    @Inject(method="onMaidSleep",at=@At("HEAD"),cancellable=true)
    private void sleepPosition(CallbackInfo ci) { if (Resting.sleep((EntityMaid)(Object)this)) ci.cancel(); }
    @Inject(method = "teleportToOwner", at = @At("HEAD"), cancellable = true)
    private void teleport(LivingEntity owner, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid m = (EntityMaid)(Object)this;
        var target = Spaces.tracking(owner);
        if (Spaces.tracking(m) != null || target != null) {
            cir.setReturnValue(owner.level() == m.level() && Spaces.teleportNear(m, target, Spaces.local(target, owner.position()), true));
        }
    }
    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void showStatus(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        EntityMaid m = (EntityMaid)(Object)this;
        if (!m.level().isClientSide() && m.isOwnedBy(player) && Homes.managed(m))
            Homes.notifyOwner(m, Homes.status((net.minecraft.server.level.ServerLevel)m.level(), Homes.binding(m)), true);
    }
}
