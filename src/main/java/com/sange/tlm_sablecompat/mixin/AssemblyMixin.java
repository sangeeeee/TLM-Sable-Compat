package com.sange.tlm_sablecompat.mixin;

import com.sange.tlm_sablecompat.BindingStore;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashSet;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public class AssemblyMixin {
    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private static void moved(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                              Iterable<BlockPos> blocks, CallbackInfo ci) {
        var set = new HashSet<BlockPos>(); blocks.forEach(p -> set.add(p.immutable()));
        BindingStore.get(level).moved(level, transform, set);
        // Picnic mats store a center address in every one of their 25 block entities.
        // Update that address using the real block move, including assembly and fragment moves.
        for (BlockPos oldPos : set) {
            var tile = transform.getLevel().getBlockEntity(transform.apply(oldPos));
            if (tile instanceof com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat mat
                    && set.contains(mat.getCenterPos()))
                mat.setCenterPos(transform.apply(mat.getCenterPos()));
        }
        if (net.neoforged.fml.ModList.get().isLoaded("tlm_maid_manager"))
            com.sange.tlm_sablecompat.ManagerAnchors.moved(level, transform, set);
    }
}
