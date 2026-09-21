package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sch246.muhc.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;

/** Loaded only by the optional MUHC bridge. A work block is not a walking destination. */
public final class Cranks {
    private Cranks() {}
    public record Approach(boolean allowed, BlockPos standing) {}
    public static Approach approach(EntityMaid maid, BlockPos crank) {
        if (!Work.blockAllowed(maid,crank)) return new Approach(false,null);
        if (!Work.active(maid)) return new Approach(true,null);
        double reach=Config.REACH_RADIUS.get();
        Vec3 target=Spaces.project(maid.level(),crank.getCenter());
        // MUHC intentionally permits operating within reach, including while sitting.
        if (maid.position().distanceToSqr(target)<=reach*reach) return new Approach(true,null);
        int radius=Math.min(8,(int)Math.ceil(reach));
        var candidates=new ArrayList<BlockPos>();
        var space=Work.space(maid);
        for (BlockPos p:BlockPos.betweenClosed(crank.offset(-radius,-radius,-radius),crank.offset(radius,radius,radius))) {
            if (!Work.blockAllowed(maid,p) || Vec3.atBottomCenterOf(p).distanceToSqr(crank.getCenter())>reach*reach) continue;
            if (maid.level().getBlockState(p.below()).getCollisionShape(maid.level(),p.below()).isEmpty()) continue;
            if (!maid.level().getBlockState(p).getCollisionShape(maid.level(),p).isEmpty()
                    || !maid.level().getBlockState(p.above()).getCollisionShape(maid.level(),p.above()).isEmpty()) continue;
            candidates.add(p.immutable());
        }
        candidates.sort(Comparator.comparingDouble(p->Spaces.world(space,Vec3.atBottomCenterOf(p)).distanceToSqr(maid.position())));
        return candidates.stream().limit(32).filter(p->Work.reachable(maid,p,0)).findFirst()
                .map(p->new Approach(true,p)).orElseGet(()->new Approach(false,null));
    }
}
