package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class Navigation {
    private Navigation() {}
    private static final Map<EntityMaid, State> STATES = new WeakHashMap<>();
    public static State state(EntityMaid maid) { return STATES.computeIfAbsent(maid, m -> new State()); }
    public static final class State {
        public long retryAt;
        public PositionTracker followTarget;
        public UUID ownerFrame;
        public boolean following;
        public boolean suspended;
        public String notified = "";
        public UUID maidFrame;
        public boolean frameInitialized;
    }
    public static void refresh(EntityMaid maid) {
        State st = state(maid);
        var here = Spaces.tracking(maid);
        UUID frame = here == null ? null : here.getUniqueId();
        if (st.frameInitialized && !Objects.equals(frame, st.maidFrame)) {
            clearMovement(maid);
            maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        st.frameInitialized = true; st.maidFrame = frame;
        if (st.followTarget instanceof OwnerTracker target && !target.isVisibleBy(maid)) clearFollow(maid);
    }
    public static void clearMovement(EntityMaid m) {
        m.getNavigation().stop();
        m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        m.getBrain().eraseMemory(MemoryModuleType.PATH);
    }
    public static void clearFollow(EntityMaid m) {
        State st = state(m);
        var walk = m.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (st.followTarget != null && walk.isPresent() && walk.get().getTarget() == st.followTarget) clearMovement(m);
        st.followTarget = null;
    }
    /** Returns true when the Sable-aware branch owns this follow decision. */
    public static boolean follow(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        State st = state(maid);
        SubLevel here = Spaces.tracking(maid), there = owner == null ? null : Spaces.tracking(owner);
        if (here == null && there == null && !st.following) return false;
        var previous = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (previous.isPresent() && previous.get().getTarget() instanceof net.minecraft.world.entity.ai.behavior.EntityTracker tracker
                && tracker.getEntity() == owner) {
            clearMovement(maid); // Take over a pre-existing vanilla owner target exactly once.
        }
        if (owner == null || owner.level() != maid.level() || owner.isDeadOrDying() || owner.isSpectator()
                || maid.isHomeModeEnable() || !maid.canBrainMoving()) {
            clearFollow(maid); st.following = false; return true;
        }
        UUID frame = there == null ? null : there.getUniqueId();
        if (!Objects.equals(frame, st.ownerFrame)) {
            clearFollow(maid); st.retryAt = 0; st.ownerFrame = frame;
        }
        st.following = here != null || there != null;
        if (!st.following) { clearFollow(maid); return false; }
        double distance = maid.position().distanceTo(owner.position());
        double start = maid.getRestrictRadius() - 2, teleport = start + 4;
        boolean cross = here != there;
        if ((cross && there != null) || distance >= teleport || maid.getSwimManager().isGoingToBreath()) {
            if (maid.level().getGameTime() >= st.retryAt) {
                st.retryAt = maid.level().getGameTime() + 20;
                // Only the owner's CURRENT tracked structure is considered, never nearby ships.
                if (Spaces.teleportNear(maid, there, Spaces.local(there, owner.position()), true)) {
                    if (maid.getSwimManager().isGoingToBreath()) {
                        maid.getNavigationManager().resetNavigation();
                        maid.getSwimManager().setGoingToBreath(false);
                    }
                    st.followTarget = null;
                    return true;
                }
            }
            if (cross) { clearFollow(maid); return true; }
        }
        if (cross || distance < start || !Spaces.upright(here)) return true;
        var current = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (current.isPresent() && current.get().getTarget() == st.followTarget) return true;
        PositionTracker tracker = new OwnerTracker(owner, there);
        st.followTarget = tracker;
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(tracker, 0.5f, 2));
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.EntityTracker(owner, true));
        return true;
    }
    public record OwnerTracker(LivingEntity owner, SubLevel structure) implements PositionTracker {
        @Override public Vec3 currentPosition() { return Spaces.local(structure, owner.position()); }
        @Override public BlockPos currentBlockPosition() { return BlockPos.containing(currentPosition()); }
        @Override public boolean isVisibleBy(LivingEntity entity) {
            return owner.isAlive() && owner.level() == entity.level() && Spaces.tracking(owner) == structure;
        }
    }
}
