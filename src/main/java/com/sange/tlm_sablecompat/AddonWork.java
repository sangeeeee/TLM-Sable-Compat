package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Coordinate adapters for optional tasks. Entity coordinates themselves always remain in world space. */
public final class AddonWork {
    private AddonWork() {}
    public static Vec3 position(EntityMaid maid) {
        var s = Spaces.tracking(maid);
        return s == null ? maid.position() : Spaces.local(s, maid.position());
    }
    public static BlockPos center(EntityMaid maid) {
        return maid.hasRestriction() ? maid.getRestrictCenter() : BlockPos.containing(position(maid));
    }
    public static boolean targetValid(EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get())
                .map(t -> Work.blockAllowed(maid,t.currentBlockPosition())).orElse(false);
    }
    public static void localMotion(EntityMaid maid, double x, double y, double z) {
        var s = Spaces.tracking(maid);
        if (s == null) { maid.setDeltaMovement(x,y,z); return; }
        Vec3 horizontal = Spaces.world(s,new Vec3(x,0,z)).subtract(Spaces.world(s,Vec3.ZERO));
        maid.setDeltaMovement(horizontal.x,y,horizontal.z);
    }
    public record Approach(boolean allowed, BlockPos standing) {}
    public interface BerryPath { BlockPos tlmsc$berryStanding(); }
    /** A solid workstation is interacted with from a reachable nearby standing point. */
    public static Approach approach(EntityMaid m,BlockPos block,double reach) {
        return approach(m,block,reach,p->Work.reachable(m,p,0));
    }
    public static Approach approach(EntityMaid m,BlockPos block,double reach,java.util.function.Predicate<BlockPos> reachable) {
        if(!Work.blockAllowed(m,block)) return new Approach(false,null);
        if(!Work.active(m) || Work.distance(m,block.getCenter())<=reach*reach) return new Approach(true,null);
        int r=Math.min(8,(int)Math.ceil(reach));
        var candidates=new java.util.ArrayList<BlockPos>();
        for(BlockPos p:BlockPos.betweenClosed(block.offset(-r,-r,-r),block.offset(r,r,r))) {
            if(!Work.blockAllowed(m,p) || Vec3.atBottomCenterOf(p).distanceToSqr(block.getCenter())>reach*reach) continue;
            if(m.level().getBlockState(p.below()).getCollisionShape(m.level(),p.below()).isEmpty()) continue;
            if(!m.level().getBlockState(p).getCollisionShape(m.level(),p).isEmpty()
                    || !m.level().getBlockState(p.above()).getCollisionShape(m.level(),p.above()).isEmpty()) continue;
            candidates.add(p.immutable());
        }
        candidates.sort(java.util.Comparator.comparingDouble(p->Vec3.atBottomCenterOf(p).distanceToSqr(position(m))));
        return candidates.stream().limit(32).filter(reachable).findFirst()
                .map(p->new Approach(true,p)).orElseGet(()->new Approach(false,null));
    }
}
