package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.sange.tlm_sablecompat.Homes;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.hmtl.tlmmaidmanager.network.message.TeleportMaidToWorkBlockPacket", remap=false)
public class ManagerDispatchMixin {
    @Redirect(method="lambda$handle$0", at=@At(value="INVOKE", target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/SchedulePos;setHomeModeEnable(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/core/BlockPos;)V"))
    private static void keepAnchorHome(SchedulePos schedule, EntityMaid maid, BlockPos projected) {
        Homes.sync(maid, Homes.binding(maid));
        schedule.restrictTo(maid);
    }
}
