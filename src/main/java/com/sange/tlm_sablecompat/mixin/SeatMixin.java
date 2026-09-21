package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.sange.tlm_sablecompat.Resting;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=EntitySit.class,remap=false)
public class SeatMixin implements Resting.SeatData {
    @Shadow private BlockPos associatedBlockPos;
    @Inject(method="tick",at=@At("HEAD"))
    private void move(CallbackInfo ci) { Resting.seat((EntitySit)(Object)this); }
    @Redirect(method="tickMaid",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/item/EntitySit;getYRot()F"))
    private float facing(EntitySit seat) { return Resting.seatYaw(seat); }
    public void compat$associated(BlockPos pos) { associatedBlockPos=pos; }
}
