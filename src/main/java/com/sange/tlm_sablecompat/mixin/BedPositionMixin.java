package com.sange.tlm_sablecompat.mixin;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBedTask;
import com.sange.tlm_sablecompat.Spaces;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=MaidBedTask.class,remap=false)
public class BedPositionMixin {
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;setPos(DDD)V"))
    private static void bed(EntityMaid m,double x,double y,double z) { m.setPos(Spaces.project(m.level(),new Vec3(x,y,z))); }
}
