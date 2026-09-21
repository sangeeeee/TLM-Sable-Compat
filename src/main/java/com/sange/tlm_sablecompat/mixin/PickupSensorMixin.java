package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidPickupEntitiesSensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.sange.tlm_sablecompat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaidPickupEntitiesSensor.class,remap=false)
public class PickupSensorMixin {
    @Inject(method="doTick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V",at=@At("HEAD"),cancellable=true)
    private void scan(ServerLevel level,EntityMaid m,CallbackInfo ci) {
        if (!Work.active(m)) return;
        ci.cancel();
        if (!m.isTame() || !Work.ready(m)) { m.getBrain().eraseMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get());return; }
        double radius=m.getRestrictRadius();
        AABB box=new AABB(m.hasRestriction()?m.getRestrictCenter():Spaces.localPosition(m)).inflate(radius,4,radius);
        var list=level.getEntitiesOfClass(Entity.class,Work.worldBox(Spaces.tracking(m),box),Entity::isAlive).stream()
                .filter(e->Pickup.allowed(m,e) && e.closerThan(m,radius+1) && m.canPickup(e,true))
                .filter(e->Pickup.visible(m,e,AddonWork.position(m)))
                .sorted(java.util.Comparator.comparingDouble(m::distanceToSqr)).toList();
        m.getBrain().setMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get(),list);
    }
}
