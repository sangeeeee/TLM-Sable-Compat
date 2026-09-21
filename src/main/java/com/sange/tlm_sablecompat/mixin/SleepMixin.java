package com.sange.tlm_sablecompat.mixin;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Resting;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public class SleepMixin {
    @Inject(method="stopSleeping",at=@At("HEAD"),cancellable=true)
    private void wake(CallbackInfo ci) { if ((Object)this instanceof EntityMaid m && Resting.wake(m)) ci.cancel(); }
}
