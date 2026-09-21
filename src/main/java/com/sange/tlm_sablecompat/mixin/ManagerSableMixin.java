package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.hmtl.tlmmaidmanager.compat.sable.SableCompat;
import com.hmtl.tlmmaidmanager.network.message.WorkBlockListPacket;
import com.hmtl.tlmmaidmanager.world.WorkBlockData;
import com.sange.tlm_sablecompat.ManagerAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.hmtl.tlmmaidmanager.compat.sable.SableCompat", remap=false)
public class ManagerSableMixin {
    @Inject(method="toListEntry", at=@At("HEAD"), cancellable=true)
    private static void stableAddress(ServerLevel level, WorkBlockData.Entry e, CallbackInfoReturnable<WorkBlockListPacket.Entry> cir) {
        cir.setReturnValue(new WorkBlockListPacket.Entry(e.name(), e.dimension(), e.x(), e.y(), e.z()));
    }
    @Inject(method="findWorkBlockByPos", at=@At("HEAD"), cancellable=true)
    private static void named(ServerLevel level, BlockPos pos, ServerPlayer player, CallbackInfoReturnable<WorkBlockData.Entry> cir) {
        cir.setReturnValue(WorkBlockData.get(level).getNamedEntry(level, pos, player.getUUID()));
    }
    @Inject(method="findAnyEntryByPos", at=@At("HEAD"), cancellable=true)
    private static void any(ServerLevel level, BlockPos pos, ServerPlayer player, CallbackInfoReturnable<WorkBlockData.Entry> cir) {
        cir.setReturnValue(WorkBlockData.get(level).getEntry(level, pos, player.getUUID()));
    }
    @Inject(method="teleportMaidToWorkBlock", at=@At("HEAD"), cancellable=true)
    private static void teleport(EntityMaid maid, ServerLevel level, BlockPos pos, CallbackInfoReturnable<SableCompat.Result> cir) {
        cir.setReturnValue(ManagerAnchors.teleport(maid, level, pos));
    }
    @Inject(method="registerManagedHome", at=@At("HEAD"), cancellable=true)
    private static void localHome(ServerLevel level, EntityMaid maid, BlockPos pos, BlockPos projected, CallbackInfo ci) {
        // The successful teleport already installed our local Home; do not start a competing world-Home updater.
        ci.cancel();
    }
}
