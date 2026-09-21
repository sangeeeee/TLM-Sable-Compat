package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

public final class Fishing {
    private Fishing() {}
    public interface Anchor {
        void compat$water(UUID structure,BlockPos pos);
    }
    /** The station stays in the selected work area; only the fishing water may be outside it. */
    public static boolean worldWaterAllowed(EntityMaid maid, BlockPos pos) {
        return Work.active(maid) && Work.ready(maid)
                && Sable.HELPER.getContaining(maid.level(),pos)==null
                && Work.blockAllowed(maid,Spaces.localPosition(maid));
    }
    public static boolean waterAllowed(EntityMaid maid, BlockPos pos) {
        return maid.level().hasChunkAt(pos) && (worldWaterAllowed(maid,pos) || Work.blockAllowed(maid,pos));
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
