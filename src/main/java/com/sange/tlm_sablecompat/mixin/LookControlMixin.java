package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Spaces;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LookControl.class)
public abstract class LookControlMixin {
    @Shadow @Final protected Mob mob;

    @ModifyVariable(method = "setLookAt(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true)
    private Vec3 projectTarget(Vec3 target) {
        return mob instanceof EntityMaid ? Spaces.project(mob.level(), target) : target;
    }
}
