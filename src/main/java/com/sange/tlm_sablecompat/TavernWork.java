package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarrelBlock;
import com.winexp.maidtavern.entity.MaidTavernEntities;
import com.winexp.maidtavern.maid.brewing.*;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

/** Loaded only by the optional Maid Tavern integration. Work addresses stay in plot coordinates. */
public final class TavernWork {
    private TavernWork() {}

    public static boolean scoped(EntityMaid maid, BlockPos pos) {
        return Work.active(maid) || Sable.HELPER.getContaining(maid.level(),pos)!=null;
    }

    public static boolean visible(EntityMaid maid, BlockPos pos, Vec3 feet) {
        var state=maid.level().getBlockState(pos);
        if (!(state.getBlock() instanceof BarrelBlock))
            return AddonWork.visibleFrom(maid,pos,feet,pos::equals);
        BlockPos origin=BarrelBlock.getOriginPos(pos,state);
        // The 3x3 lid must not occlude itself. A wall, floor or a different barrel still blocks access.
        return AddonWork.visibleFrom(maid,pos,feet,hit->{
            var part=maid.level().getBlockState(hit);
            return Work.blockAllowed(maid,hit) && part.getBlock() instanceof BarrelBlock
                    && part.getValue(BarrelBlock.LAYER)==AttachFace.CEILING
                    && BarrelBlock.getOriginPos(hit,part).equals(origin);
        });
    }

    public static AddonWork.Approach approach(EntityMaid maid,BlockPos pos,double reach,Predicate<BlockPos> reachable) {
        return AddonWork.approach(maid,pos,reach,reachable,feet->visible(maid,pos,feet));
    }

    public static boolean allowed(EntityMaid maid,BrewingWork work) {
        if (!Work.blockAllowed(maid,work.pos())) return false;
        if (work.type().equals(BrewingWorkTypes.ADD_INGREDIENTS)) {
            var session=maid.getBrain().getMemory(MaidTavernEntities.BREWING_SESSION.get()).orElse(null);
            if(session==null || session.barrelPos().isEmpty()) return false;
            BlockPos origin=session.barrelPos().get();
            return origin.above(2).equals(work.pos()) && Work.blockAllowed(maid,origin);
        }
        return true;
    }

    public static boolean close(EntityMaid maid,BrewingWork work) {
        return allowed(maid,work) && Work.distance(maid,work.pos().getCenter())<=work.closeEnoughDist()*work.closeEnoughDist()
                && visible(maid,work.pos(),AddonWork.position(maid));
    }

    public static void clearWork(EntityMaid maid) {
        if (MaidBrewingStateManager.isWorking(maid)) MaidBrewingStateManager.stopWork(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getNavigation().stop();
    }

    public static boolean walk(EntityMaid maid,BrewingWork work) {
        var approach=approach(maid,work.pos(),work.closeEnoughDist(),p->Work.reachable(maid,p,0));
        if(!approach.allowed()) return false;
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,new BlockPosTracker(work.pos()));
        if(approach.standing()!=null)
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(approach.standing(),work.movementSpeed(),0));
        else {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getNavigation().stop();
        }
        return true;
    }
}
