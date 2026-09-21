package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitAttribute;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Pickup destinations are standing positions, not necessarily the item's air block. */
public final class Pickup {
    private Pickup() {}
    public static BlockPos address(EntityMaid m,Entity e) {
        return BlockPos.containing(Spaces.local(Spaces.tracking(m),e.position()).add(1e-4,1e-4,1e-4));
    }
    public static boolean allowed(EntityMaid m,Entity e) {
        if (e.level()!=m.level() || Spaces.tracking(e)!=Spaces.tracking(m)) return false;
        if (Work.active(m) && !Work.ready(m)) return false;
        BlockPos p=address(m,e);
        return Work.blockAllowed(m,p) && m.isWithinRestriction(p);
    }
    private static double radius(EntityMaid m) {
        var attribute=m.getAttribute(InitAttribute.MAID_PICKUP_RANGE);
        return attribute==null ? .5 : attribute.getValue();
    }
    private static boolean raised(Vec3 feet,Vec3 item) {
        double dy=item.y-feet.y;
        // Small allowance for the item entity resting/bobbing just above a one-block counter.
        return dy>.5 && dy<=1.25;
    }
    public static boolean near(EntityMaid m,Entity e,Vec3 feet) {
        var s=Spaces.tracking(m);
        Vec3 item=Spaces.local(s,e.position());
        double r=radius(m),half=m.getBbWidth()/2;
        AABB box=new AABB(feet.x-half-r,feet.y-r,feet.z-half-r,
                feet.x+half+r,feet.y+m.getBbHeight()+r,feet.z+half+r);
        if (e instanceof ItemEntity && raised(feet,item)) {
            // Reach the centre of a neighbouring counter without requiring a jump onto it.
            double horizontal=Math.max(1,half+r);
            box=box.minmax(new AABB(feet.x-horizontal,feet.y,feet.z-horizontal,
                    feet.x+horizontal,feet.y+1.25,feet.z+horizontal));
        }
        return box.inflate(1e-4).contains(item);
    }
    public static boolean visible(EntityMaid m,Entity e,Vec3 feet) {
        var s=Spaces.tracking(m);
        Vec3 eye=m.getEyePosition().add(Spaces.world(s,feet).subtract(m.position()));
        Vec3 end=e.getBoundingBox().getCenter();
        if (eye.distanceToSqr(end)>64*64) return false;
        var ray=new ClipContext(Spaces.local(s,eye),Spaces.local(s,end),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m);
        ((ClipContextExtension)ray).sable$setDoNotProject(true);
        if (m.level().clip(ray).getType()!=HitResult.Type.MISS) return false;
        if (s==null) return true;
        ray=new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m);
        ((ClipContextExtension)ray).sable$setIgnoredSubLevel(s);
        return m.level().clip(ray).getType()==HitResult.Type.MISS;
    }
    public static BlockPos standing(EntityMaid m,Entity item,MaidPathFindingBFS bfs) {
        BlockPos target=address(m,item);
        var candidates=new java.util.ArrayList<BlockPos>();
        for(BlockPos p:BlockPos.betweenClosed(target.offset(-1,-1,-1),target.offset(1,0,1))) {
            Vec3 feet=Vec3.atBottomCenterOf(p);
            if (!Work.blockAllowed(m,p) || !m.isWithinRestriction(p) || !near(m,item,feet)) continue;
            if (m.level().getBlockState(p.below()).getCollisionShape(m.level(),p.below()).isEmpty()
                    || !m.level().getBlockState(p).getCollisionShape(m.level(),p).isEmpty()
                    || !m.level().getBlockState(p.above()).getCollisionShape(m.level(),p.above()).isEmpty()) continue;
            if (visible(m,item,feet)) candidates.add(p.immutable());
        }
        candidates.sort(java.util.Comparator.comparingDouble(p->Vec3.atBottomCenterOf(p).distanceToSqr(AddonWork.position(m))));
        var stand=candidates.stream().filter(bfs::canPathReach).findFirst();
        // Preserve native paths onto stairs/slabs and other surfaces not represented by an empty standing cell.
        return stand.orElseGet(()->bfs.canPathReach(target) ? target : null);
    }
    public static AABB queryBox(EntityMaid m,AABB original) {
        Vec3 p=AddonWork.position(m);
        AABB raised=new AABB(p.x-1.15,p.y+.5,p.z-1.15,p.x+1.15,p.y+1.5,p.z+1.15);
        return original.minmax(Work.worldBox(Spaces.tracking(m),raised));
    }
}
