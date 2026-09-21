package com.sange.tlm_sablecompat.mixin;

import com.hmtl.tlmmaidmanager.world.WorkBlockData;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.hmtl.tlmmaidmanager.world.WorkBlockData", remap=false)
public class ManagerWorkDataMixin {
    @Shadow @Final private Map<UUID, Map<String, WorkBlockData.Entry>> playerEntries;

    @Inject(method="moveEntry", at=@At("RETURN"))
    private void moveAddressWithKey(String oldKey, String newKey, CallbackInfoReturnable<Boolean> cir) {
        if (oldKey.equals(newKey)) return;
        // Manager moves the map key during assembly but leaves the value (and saved address) behind.
        String[] parts = newKey.split("\\|");
        String[] xyz = parts[1].split(",");
        for (var entries : playerEntries.values()) {
            WorkBlockData.Entry entry = entries.get(newKey);
            if (entry != null) entries.put(newKey, new WorkBlockData.Entry(entry.name(), parts[0],
                    Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
        }
    }
}
