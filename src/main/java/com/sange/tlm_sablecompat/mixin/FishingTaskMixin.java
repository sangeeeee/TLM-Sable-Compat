package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ride.MaidRideFindWaterTask;
import com.github.tartaricacid.touhoulittlemaid.api.entity.fishing.IFishingType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaidRideFindWaterTask.class,remap=false)
public abstract class FishingTaskMixin {
    @Shadow private BlockPos waterPos;
    @Shadow private int searchRange;
    @Shadow protected abstract void searchForDestination(ServerLevel level,EntityMaid maid,ItemStack rod);
    @Unique private boolean compat$worldSearch;

    @Redirect(method="searchForDestination",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;getBrainSearchPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos center(EntityMaid maid) {
        return compat$worldSearch ? maid.blockPosition() : maid.getBrainSearchPos();
    }
    @Inject(method="searchForDestination",at=@At("RETURN"))
    private void worldFallback(ServerLevel level,EntityMaid maid,ItemStack rod,CallbackInfo ci) {
        if (compat$worldSearch || waterPos!=null || !Fishing.worldWaterAllowed(maid,maid.blockPosition())) return;
        // Reuse the native bounded scan and its check-rate/cached-target behavior once more in world space.
        compat$worldSearch=true;
        try { searchForDestination(level,maid,rod); }
        finally { compat$worldSearch=false; }
    }
    @Redirect(method={"posCheck","searchForDestination"},at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean restriction(EntityMaid maid,BlockPos pos) {
        if (Fishing.worldWaterAllowed(maid,pos))
            return maid.position().distanceToSqr(Vec3.atLowerCornerOf(pos)) < (double)searchRange*searchRange;
        return !compat$worldSearch && maid.isWithinRestriction(pos);
    }
    @Redirect(method="posCheck",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double distance(EntityMaid maid,Vec3 point) {
        return Fishing.worldWaterAllowed(maid,BlockPos.containing(point)) ? maid.position().distanceToSqr(point) : Work.distance(maid,point);
    }
    @Redirect(method={"start","searchForDestination"},at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/api/entity/fishing/IFishingType;suitableFishingHook(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean water(IFishingType type,EntityMaid maid,Level level,ItemStack rod,BlockPos pos) {
        return Fishing.waterAllowed(maid,pos) && type.suitableFishingHook(maid,level,rod,pos);
    }
    @Redirect(method="start",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/api/entity/fishing/IFishingType;getFishingHook(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lcom/github/tartaricacid/touhoulittlemaid/entity/projectile/MaidFishingHook;"))
    private MaidFishingHook hook(IFishingType type,EntityMaid maid,Level level,ItemStack rod,Vec3 point) {
        var hook=type.getFishingHook(maid,level,rod,point);
        Fishing.attach(hook,BlockPos.containing(point));
        return hook; // Sable projects this local spawn once, in addFreshEntity.
    }
}
