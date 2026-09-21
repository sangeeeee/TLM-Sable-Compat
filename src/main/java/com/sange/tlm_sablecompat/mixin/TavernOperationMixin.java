package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.TavernWork;
import com.winexp.maidtavern.maid.brewing.MaidBrewingStateManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets={"com.winexp.maidtavern.maid.brewing.storage.MaidBrewingStorageOperationTask",
        "com.winexp.maidtavern.maid.brewing.bottle.MaidBrewingPlaceBottleTask",
        "com.winexp.maidtavern.maid.brewing.bottle.MaidBrewingTakeBottleTask"},remap=false)
public class TavernOperationMixin {
    @Inject(method="start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",at=@At("HEAD"),cancellable=true)
    private void interact(ServerLevel level,EntityMaid maid,long time,CallbackInfo ci) {
        var work=MaidBrewingStateManager.getWork(maid);
        if(work==null || TavernWork.scoped(maid,work.pos()) && !work.isCloseEnough(maid)) ci.cancel();
    }
}
