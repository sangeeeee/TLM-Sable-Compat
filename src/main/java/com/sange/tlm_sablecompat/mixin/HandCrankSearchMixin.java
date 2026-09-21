package com.sange.tlm_sablecompat.mixin;

import com.sange.tlm_sablecompat.PoiSearch;
import com.sange.tlm_sablecompat.Spaces;
import com.sange.tlm_sablecompat.Work;
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
    @Redirect(method="findCrankHandle",at=@At(value="INVOKE",target="Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> selectedStructure(Stream<PoiRecord> records,Function<PoiRecord,BlockPos> mapper,EntityMaid maid,ServerLevel level) {
        return records.map(mapper).filter(p->!Work.active(maid) || Work.reachable(maid,p,1));
    }
    @Redirect(method="getNearestReachableCrankPosition",at=@At(value="INVOKE",target="Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> selectedInteraction(Stream<PoiRecord> records,Function<PoiRecord,BlockPos> mapper,EntityMaid maid,ServerLevel level,Predicate<BlockPos> predicate) {
        return records.map(mapper).filter(p->!Work.active(maid) || Work.reachable(maid,p,1));
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
