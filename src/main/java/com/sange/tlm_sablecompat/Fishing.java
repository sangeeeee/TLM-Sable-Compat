package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

public final class Fishing {
    private Fishing() {}
    public interface Anchor {
        void compat$water(UUID structure,BlockPos pos);
    }
    public static void attach(MaidFishingHook hook,BlockPos pos) {
        var s=Sable.HELPER.getContaining(hook.level(),pos);
        if (s==null || !(hook.level() instanceof ServerLevel level)) return;
        Binding b=new Binding(false);b.touchedStructure=true;
        b.points.add(Spaces.point(level,s,pos,pos));
        hook.getPersistentData().putUUID(Resting.KEY,BindingStore.get(level).add(b));
        ((Anchor)hook).compat$water(s.getUniqueId(),pos);
    }
}
