package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.*;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;

/** Kitchen overrides the deprecated two-argument method; TLM 1.5.3 calls the BFS overload. */
@Pseudo
@Mixin(targets="com.github.wallev.maidsoulkitchen.task.farm.TaskBerryFarm$1",remap=false)
public abstract class KitchenBerryPathMixin extends MaidMoveToBlockTask implements AddonWork.BerryPath {
    @Unique private BlockPos tlmsc$standing;
    @Override public BlockPos tlmsc$berryStanding() { return tlmsc$standing; }
    protected KitchenBerryPathMixin(float speed,int vertical) { super(speed,vertical); }
    @Override
    protected boolean checkPathReach(EntityMaid m,MaidPathFindingBFS bfs,BlockPos pos) {
        if (!Work.active(m)) return super.checkPathReach(m,bfs,pos);
        // Reuse the already prepared evaluator; nested Navigation.createPath would reset it.
        var approach=AddonWork.approach(m,pos,2,bfs::canPathReach);
        tlmsc$standing=approach.standing();
        return approach.allowed();
    }
}
