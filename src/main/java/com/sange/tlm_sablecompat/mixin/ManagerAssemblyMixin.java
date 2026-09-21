package com.sange.tlm_sablecompat.mixin;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.hmtl.tlmmaidmanager.compat.sable.SableCompatImpl", remap=false)
public class ManagerAssemblyMixin {
    @Inject(method="endAssembly", at=@At(value="INVOKE", target="Lcom/hmtl/tlmmaidmanager/world/WorkBlockData;get(Lnet/minecraft/server/level/ServerLevel;)Lcom/hmtl/tlmmaidmanager/world/WorkBlockData;"), cancellable=true)
    private static void exactMovedAddresses(ServerLevel level, SubLevel structure, CallbackInfo ci) {
        // Keep Manager's assembly-suppression cleanup above this call, but replace its world-only
        // inverse-pose remap with the exact moveBlocks transform (also valid for ship fragments).
        ci.cancel();
    }
}
