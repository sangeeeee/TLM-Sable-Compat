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
        public UUID waitingFrame;
        public String waitingDimension;
        public Vec3 safeLocal;
        public long leftAt = -1;
        public net.minecraft.world.entity.schedule.Activity activity;
        public boolean homeMode;
        public boolean resetTasks;
    }
    public static void refresh(EntityMaid maid) {
        State st = state(maid);
        var here = Spaces.tracking(maid);
        UUID frame = here == null ? null : here.getUniqueId();
        long now = maid.level().getGameTime();
        if (here != null && Spaces.support(maid.level(), here, maid.position()) != null) {
            st.waitingFrame = frame; st.waitingDimension = maid.level().dimension().location().toString();
            st.safeLocal = Spaces.local(here, maid.position()); st.leftAt = -1;
        } else if (here == null && st.waitingFrame != null && st.leftAt < 0) st.leftAt = now;
        if (st.frameInitialized && !Objects.equals(frame, st.maidFrame)) {
            st.resetTasks = true;
            clearMovement(maid);
            maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        st.frameInitialized = true; st.maidFrame = frame;
        if (st.followTarget instanceof OwnerTracker target && !target.isVisibleBy(maid)) clearFollow(maid);
        if (st.resetTasks || (here != null || waitingSpace(maid) != null || Homes.managed(maid)) && st.activity != null
                && (st.activity != maid.getScheduleDetail() || st.homeMode != maid.isHomeModeEnable())) {
            maid.getBrain().stopAll((net.minecraft.server.level.ServerLevel)maid.level(), maid);
            Resting.leave(maid);
            clearMovement(maid);
            maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
            Combat.clear(maid);
            st.resetTasks = false;
        }
        st.activity = maid.getScheduleDetail(); st.homeMode = maid.isHomeModeEnable();
        if (maid.isHomeModeEnable()) {
            Homes.sync(maid,Homes.binding(maid));
            maid.getSchedulePos().restrictTo(maid);
        }
    }
    public static SubLevel waitingSpace(EntityMaid m) {
        State st = state(m);
        return m.level().dimension().location().toString().equals(st.waitingDimension) ? Spaces.resolve(m.level(),st.waitingFrame) : null;
    }
    public static boolean departureConfirmed(EntityMaid m) {
        State st = state(m);
        return Spaces.tracking(m) != null || st.leftAt >= 0 && m.level().getGameTime() - st.leftAt >= 10;
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
        SubLevel waiting = waitingSpace(maid);
        if (here == null && there == null && waiting == null && !st.following) return false;
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
        st.following = here != null || there != null || waiting != null;
        if (!st.following) { clearFollow(maid); return false; }
        double distance = maid.position().distanceTo(owner.position());
        double start = maid.getRestrictRadius() - 2, teleport = start + 4;
        boolean cross = here != there;
        boolean lostWaiting = here == null && waiting != null && departureConfirmed(maid);
        if (cross || lostWaiting || distance >= teleport || maid.getSwimManager().isGoingToBreath()) {
            if (maid.level().getGameTime() >= st.retryAt) {
                st.retryAt = maid.level().getGameTime() + 20;
                // Only the owner's CURRENT tracked structure is considered, never nearby ships.
                if ((here != null || waiting == null || departureConfirmed(maid)) && Spaces.teleportNear(maid, there, Spaces.local(there, owner.position()), true)) {
                    if (there == null) { st.waitingFrame = null; st.safeLocal = null; }
                    if (maid.getSwimManager().isGoingToBreath()) {
                        maid.getNavigationManager().resetNavigation();
                        maid.getSwimManager().setGoingToBreath(false);
                    }
                    st.followTarget = null;
                    return true;
                }
                if (there == null && here == null && waiting != null && departureConfirmed(maid) && st.safeLocal != null) {
                    Spaces.teleportNear(maid,waiting,st.safeLocal,false);
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
