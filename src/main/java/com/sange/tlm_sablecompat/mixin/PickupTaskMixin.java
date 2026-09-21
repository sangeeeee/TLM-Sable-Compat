package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.*;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.sange.tlm_sablecompat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaidPickupEntitiesTask.class,remap=false)
public class PickupTaskMixin {
    @Shadow @Final private float speedModifier;
    @Inject(method="start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void approach(ServerLevel level,EntityMaid m,long time,CallbackInfo ci) {
        ci.cancel();
        if (Work.active(m) && !Work.ready(m)) return;
        var bfs=new MaidPathFindingBFS(m.getNavigation().getNodeEvaluator(),level,m);
        try {
            for(var e:m.getBrain().getMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get()).orElse(java.util.List.of())) {
                if (!e.isAlive() || e.isInWater() || !Pickup.allowed(m,e) || !m.canPickup(e,true)) continue;
                if (e instanceof ItemEntity) {
                    if (Pickup.near(m,e,AddonWork.position(m)) && Pickup.visible(m,e,AddonWork.position(m))) break;
                    var stand=Pickup.standing(m,e,bfs);
                    if (stand==null) continue;
                    if (!Work.active(m) && stand.equals(e.blockPosition())) BehaviorUtils.setWalkAndLookTargetMemories(m,e,speedModifier,0);
                    else {
                        m.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(stand,speedModifier,0));
                        m.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,new EntityTracker(e,true));
                    }
                } else {
                    if (!bfs.canPathReach(Pickup.address(m,e))) continue;
                    BehaviorUtils.setWalkAndLookTargetMemories(m,e,speedModifier,0);
                }
                break;
            }
        } finally { bfs.finish(); }
    }
}
