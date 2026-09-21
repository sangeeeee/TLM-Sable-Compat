package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Work;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager",remap=false)
public class KitchenContainersMixin {
    @Shadow @Final private EntityMaid maid;
    @Inject(method="isPosZone",at=@At("HEAD"),cancellable=true)
    private void outside(BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (Work.active(maid) || !Work.blockAllowed(maid,pos))
            cir.setReturnValue(!Work.blockAllowed(maid,pos) || Work.distance(maid,pos.getCenter())>maid.getRestrictRadius()*maid.getRestrictRadius());
    }
}
