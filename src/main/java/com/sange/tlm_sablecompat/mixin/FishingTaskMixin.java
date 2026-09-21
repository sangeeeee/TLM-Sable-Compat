package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ride.MaidRideFindWaterTask;
import com.github.tartaricacid.touhoulittlemaid.api.entity.fishing.IFishingType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=MaidRideFindWaterTask.class,remap=false)
public class FishingTaskMixin {
    @Redirect(method="posCheck",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double distance(EntityMaid maid,Vec3 point) { return Work.distance(maid,point); }
    @Redirect(method={"start","searchForDestination"},at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/api/entity/fishing/IFishingType;suitableFishingHook(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean water(IFishingType type,EntityMaid maid,Level level,ItemStack rod,BlockPos pos) {
        return Work.blockAllowed(maid,pos) && type.suitableFishingHook(maid,level,rod,pos);
    }
    @Redirect(method="start",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/api/entity/fishing/IFishingType;getFishingHook(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lcom/github/tartaricacid/touhoulittlemaid/entity/projectile/MaidFishingHook;"))
    private MaidFishingHook hook(IFishingType type,EntityMaid maid,Level level,ItemStack rod,Vec3 point) {
        var hook=type.getFishingHook(maid,level,rod,point);
        Fishing.attach(hook,BlockPos.containing(point));
        return hook; // Sable projects this local spawn once, in addFreshEntity.
    }
}
