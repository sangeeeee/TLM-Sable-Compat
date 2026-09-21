package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IBarrel;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.TapBlock;
import com.sange.tlm_sablecompat.Work;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.winexp.maidtavern.maid.brewing.TaskBrewing",remap=false)
public class TavernTargetsMixin {
    @Inject(method="isBarrelValid",at=@At("HEAD"),cancellable=true)
    private void barrel(EntityMaid maid,IBarrel barrel,CallbackInfoReturnable<Boolean> cir) {
        if(barrel instanceof BlockEntity be && (!Work.blockAllowed(maid,be.getBlockPos())
                || !Work.blockAllowed(maid,be.getBlockPos().above(2)))) cir.setReturnValue(false);
    }
    @Inject(method={"isBottleValid","shouldPlaceBottle"},at=@At("HEAD"),cancellable=true)
    private void bottle(EntityMaid maid,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(pos==null || !Work.blockAllowed(maid,pos) || !Work.blockAllowed(maid,pos.above())) { cir.setReturnValue(false);return; }
        var tap=maid.level().getBlockState(pos.above());
        if(tap.getBlock() instanceof TapBlock && !Work.blockAllowed(maid,pos.above().relative(tap.getValue(TapBlock.FACING).getOpposite())))
            cir.setReturnValue(false);
    }
}
