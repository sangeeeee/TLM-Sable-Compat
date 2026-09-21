package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** The millstone itself moves its bound mob; fixing just the task's search would teleport it into plot storage. */
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.MillstoneBlockEntity",remap=false)
public class MillstoneBindingMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double distance(Mob mob,Vec3 center) {
        return mob instanceof EntityMaid m ? Work.distance(m,center) : mob.distanceToSqr(center);
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;moveTo(DDDFF)V"))
    private void move(Mob mob,double x,double y,double z,float yaw,float pitch) {
        Vec3 p=new Vec3(x,y,z);
        if (mob instanceof EntityMaid m && Work.active(m)) {
            if (!Work.blockAllowed(m,net.minecraft.core.BlockPos.containing(p))) return;
            p=Spaces.world(Work.space(m),p);
            Vec3 facing=Spaces.world(Work.space(m),new Vec3(-Math.sin(Math.toRadians(yaw)),0,Math.cos(Math.toRadians(yaw))))
                    .subtract(Spaces.world(Work.space(m),Vec3.ZERO));
            yaw=(float)Math.toDegrees(Math.atan2(-facing.x,facing.z));
        }
        mob.moveTo(p.x,p.y,p.z,yaw,pitch);
    }
}
