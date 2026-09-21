package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;

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
    public static boolean board(EntityMaid m,BlockPos p) {
        String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(m.level().getBlockState(p).getBlock()).toString();
        return id.equals("kaleidoscope_cookery:chopping_board") || id.equals("farmersdelight:cutting_board");
    }
    /** Adjacent cells on all three local axes, including diagonal neighbours. */
    public static boolean boardRange(Vec3 feet,BlockPos p) {
        // Plot coordinates are large; rotation round trips can put an exact floor a few ulps below its cell.
        BlockPos cell=BlockPos.containing(feet.add(1e-4,1e-4,1e-4));
        return Math.abs((long)cell.getX()-p.getX())<=1 && Math.abs((long)cell.getY()-p.getY())<=1
                && Math.abs((long)cell.getZ()-p.getZ())<=1;
    }
    public static boolean visible(EntityMaid m,BlockPos p) {
        return Work.blockAllowed(m,p) && visibleFrom(m,p,position(m));
    }
    private static boolean visibleFrom(EntityMaid m,BlockPos p,Vec3 feet) {
        return visibleFrom(m,p,feet,p::equals);
    }
    /** Multiblock interactions may accept another part of the same workstation as the first hit. */
    public static boolean visibleFrom(EntityMaid m,BlockPos p,Vec3 feet,java.util.function.Predicate<BlockPos> targetPart) {
        var level=m.level();
        if (!level.hasChunkAt(p)) return false;
        var s=Spaces.tracking(m);
        Vec3 eye=m.getEyePosition().add(Spaces.world(s,feet).subtract(m.position()));
        // Aim inside the top of the actual collision shape: thin boards must not be
        // treated as full cubes. The target itself is a valid first hit, intervening blocks are not.
        var shape=level.getBlockState(p).getCollisionShape(level,p);
        double top=shape.isEmpty() ? .5 : Math.min(.999,shape.max(net.minecraft.core.Direction.Axis.Y));
        Vec3 point=new Vec3(p.getX()+.5,p.getY()+top-.001,p.getZ()+.5);
        if (Spaces.local(s,eye).distanceToSqr(point)>64*64) return false;
        var localRay=new ClipContext(Spaces.local(s,eye),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m);
        ((ClipContextExtension)localRay).sable$setDoNotProject(true);
        var hit=level.clip(localRay);
        if (hit.getType()!=HitResult.Type.MISS && !targetPart.test(hit.getBlockPos())) return false;
        if (s==null) return true;
        // The direct local check also works immediately after a pose change, before broad-phase bounds update.
        var worldRay=new ClipContext(eye,Spaces.world(s,point),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m);
        ((ClipContextExtension)worldRay).sable$setIgnoredSubLevel(s);
        return level.clip(worldRay).getType()==HitResult.Type.MISS;
    }
    public static boolean canInteract(EntityMaid m,BlockPos p,double reach) {
        return Work.blockAllowed(m,p) && (board(m,p) ? boardRange(position(m),p) : Work.distance(m,p.getCenter())<=reach*reach)
                && visible(m,p);
    }
    public static boolean targetInteractable(EntityMaid m,double reach) {
        return m.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(t->canInteract(m,t.currentBlockPosition(),reach)).orElse(false);
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
        return approach(m,block,reach,reachable,feet->visibleFrom(m,block,feet));
    }
    public static Approach approach(EntityMaid m,BlockPos block,double reach,java.util.function.Predicate<BlockPos> reachable,
                                    java.util.function.Predicate<Vec3> visible) {
        if(!Work.blockAllowed(m,block)) return new Approach(false,null);
        boolean board=board(m,block);
        if((!Work.active(m) && !board) || (board ? boardRange(position(m),block)
                : Work.distance(m,block.getCenter())<=reach*reach) && visible.test(position(m))) return new Approach(true,null);
        int r=board ? 1 : Math.min(8,(int)Math.ceil(reach));
        var candidates=new java.util.ArrayList<BlockPos>();
        for(BlockPos p:BlockPos.betweenClosed(block.offset(-r,-r,-r),block.offset(r,r,r))) {
            if(!Work.blockAllowed(m,p) || (!board && Vec3.atBottomCenterOf(p).distanceToSqr(block.getCenter())>reach*reach)) continue;
            if(m.level().getBlockState(p.below()).getCollisionShape(m.level(),p.below()).isEmpty()) continue;
            if(!m.level().getBlockState(p).getCollisionShape(m.level(),p).isEmpty()
                    || !m.level().getBlockState(p.above()).getCollisionShape(m.level(),p.above()).isEmpty()) continue;
            if(!visible.test(Vec3.atBottomCenterOf(p))) continue;
            candidates.add(p.immutable());
        }
        candidates.sort(java.util.Comparator.comparingDouble(p->Vec3.atBottomCenterOf(p).distanceToSqr(position(m))));
        return candidates.stream().limit(32).filter(reachable).findFirst()
                .map(p->new Approach(true,p)).orElseGet(()->new Approach(false,null));
    }
}
