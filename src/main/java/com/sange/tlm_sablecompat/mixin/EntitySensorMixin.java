package com.sange.tlm_sablecompat.mixin;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidNearestLivingEntitySensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.sange.tlm_sablecompat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MaidNearestLivingEntitySensor.class,remap=false)
public class EntitySensorMixin {
    @Inject(method="doTick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V",at=@At("TAIL"))
    private void filter(ServerLevel level,EntityMaid m,CallbackInfo ci) {
        if (!Work.active(m)) return;
        var list=m.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(java.util.List.of()).stream()
                .filter(e->Work.ready(m) && (m.getTask() instanceof IAttackTask || Work.entityReachable(m,e)))
                .toList();
        m.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES,list);
        m.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,new NearestVisibleLivingEntities(m,list));
    }
}
