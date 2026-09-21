package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Spaces;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = MaidPathFindingBFS.class, remap = false)
public class BfsMixin {
    @Redirect(method = "<init>(Lnet/minecraft/world/level/pathfinder/NodeEvaluator;Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;FI)V",
            at = @At(value = "INVOKE", target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos center(EntityMaid maid) { return Spaces.localPosition(maid); }
}
