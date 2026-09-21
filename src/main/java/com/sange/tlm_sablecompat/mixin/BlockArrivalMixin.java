package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Work;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value={MaidArriveAtBlockTask.class,MaidFarmPlantTask.class,MaidTorchPlaceTask.class,MaidStealEdibleUseTask.class},remap=false)
public class BlockArrivalMixin {
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double blockDistance(EntityMaid maid, Vec3 point) { return Work.distance(maid,point); }
}
