package com.sange.tlm_sablecompat.mixin;

import com.sange.tlm_sablecompat.PoiSearch;
import com.sange.tlm_sablecompat.Spaces;
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
