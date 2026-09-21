package com.sange.tlm_sablecompat.mixin;

import com.sange.tlm_sablecompat.BindingStore;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelContainer.class, remap = false)
public class ContainerMixin {
    @Inject(method = "removeSubLevel(IILdev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V", at = @At("HEAD"))
    private void removed(int x, int z, SubLevelRemovalReason reason, CallbackInfo ci) {
        SubLevelContainer self = (SubLevelContainer)(Object)this;
        if (reason != SubLevelRemovalReason.REMOVED || !(self.getLevel() instanceof ServerLevel level)) return;
        var s = self.getSubLevel(x, z);
        if (s != null) BindingStore.get(level).removed(level.dimension().location().toString(), s.getUniqueId());
    }
}
