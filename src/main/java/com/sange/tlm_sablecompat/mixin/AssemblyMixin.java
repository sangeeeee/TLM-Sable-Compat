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
        if (net.neoforged.fml.ModList.get().isLoaded("tlm_maid_manager"))
            com.sange.tlm_sablecompat.ManagerAnchors.moved(level, transform, set);
    }
}
