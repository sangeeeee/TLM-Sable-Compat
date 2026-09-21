package com.sange.tlm_sablecompat.mixin;

import com.sange.tlm_sablecompat.PoiSearch;
import com.sange.tlm_sablecompat.Spaces;
import com.sange.tlm_sablecompat.Work;
import com.sange.tlm_sablecompat.Cranks;
import com.sange.tlm_sablecompat.Navigation;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import java.util.stream.Stream;

/** Optional MUHC 1.6.2 bridge: its POI query and distance checks expect world coordinates. */
@Pseudo
@Mixin(targets = "com.sch246.muhc.maid.task.UseHandCrank", remap = false)
public abstract class HandCrankSearchMixin {
    @org.spongepowered.asm.mixin.Shadow private BlockPos crankPos;
    @Redirect(method="findCrankHandle",at=@At(value="INVOKE",target="Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> selectedStructure(Stream<PoiRecord> records,Function<PoiRecord,BlockPos> mapper,EntityMaid maid,ServerLevel level) {
        return records.map(mapper).filter(p->Cranks.approach(maid,p).allowed());
    }
    @Redirect(method="getNearestReachableCrankPosition",at=@At(value="INVOKE",target="Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> selectedInteraction(Stream<PoiRecord> records,Function<PoiRecord,BlockPos> mapper,EntityMaid maid,ServerLevel level,Predicate<BlockPos> predicate) {
        // This query already applies MUHC's real interaction radius. No walking is needed.
        return records.map(mapper).filter(p->Work.blockAllowed(maid,p));
    }
    @Redirect(method="checkExtraStartConditions",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ai/behavior/BehaviorUtils;setWalkAndLookTargetMemories(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;FI)V"))
    private void approach(net.minecraft.world.entity.LivingEntity entity,BlockPos worldTarget,float speed,int tolerance) {
        EntityMaid maid=(EntityMaid)entity;
        if (!Work.active(maid)) {
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(entity,worldTarget,speed,tolerance);
            return;
        }
        var plan=Cranks.approach(maid,crankPos);
        if (plan.allowed() && plan.standing()!=null)
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(entity,plan.standing(),speed,0);
        else Navigation.clearMovement(maid);
    }
    @Inject(method="canStillUse",at=@At("RETURN"),cancellable=true)
    private void validSpace(ServerLevel level,EntityMaid maid,long time,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && crankPos!=null && !Work.blockAllowed(maid,crankPos)) cir.setReturnValue(false);
    }
    @ModifyVariable(method = "getCrankInDoubleCircleUnion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 worldCenter(Vec3 value, ServerLevel level, Vec3 center, int centerRadius, Vec3 maid, int maidRadius) {
        return Spaces.project(level, value);
    }

    @Redirect(method = "getCrankInDoubleCircleUnion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ChunkPos;rangeClosed(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/stream/Stream;"))
    private Stream<ChunkPos> separateAreas(ChunkPos ignoredMin, ChunkPos ignoredMax, ServerLevel level,
                                           Vec3 center, int centerRadius, Vec3 maid, int maidRadius) {
        // Even two genuine world-space centers may be far apart after releasing a stored maid.
        return PoiSearch.aroundBoth(center, centerRadius, maid, maidRadius);
    }
}
