package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.LocalAvoidance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathNavigation.class)
public abstract class NavigationRecoveryMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected Path path;
    @Shadow protected double speedModifier;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void recover(CallbackInfo ci) {
        if(mob instanceof EntityMaid maid && LocalAvoidance.tick(maid,path,speedModifier)) ci.cancel();
    }
}
