package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

public final class Combat {
    private Combat() {}
    public record Plan(boolean allowed, BlockPos destination) {}
    private record Cached(UUID target, long until, Vec3 maid, Vec3 enemy, Object weapon, Object task, Plan plan) {}
    private static final Map<EntityMaid,Cached> CACHE = new WeakHashMap<>();
    private static final Map<EntityMaid,Map<UUID,Long>> REJECTED = new WeakHashMap<>();
    public static void clear(EntityMaid maid) { CACHE.remove(maid); }
    public static void afterTransfer(EntityMaid maid) {
        maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(t ->
                REJECTED.computeIfAbsent(maid,k->new HashMap<>()).put(t.getUUID(),maid.level().getGameTime()+100));
        clear(maid);
    }
    public static boolean ranged(EntityMaid m) { return m.getTask() instanceof IRangedAttackTask; }
    public static double range(EntityMaid m) {
        return m.searchRadius();
    }
    public static boolean canHitHere(EntityMaid m, LivingEntity t) {
        return Work.ready(m) && (ranged(m) ? m.distanceToSqr(t) <= range(m)*range(m) : m.isWithinMeleeAttackRange(t)) && m.hasLineOfSight(t);
    }
    public static Plan plan(EntityMaid m, LivingEntity t) {
        long now = m.level().getGameTime();
        var rejected = REJECTED.get(m);
        if (rejected != null) {
            rejected.values().removeIf(expiry -> expiry <= now);
            if (rejected.containsKey(t.getUUID())) return new Plan(false,null);
        }
        if (!Work.ready(m) || !t.isAlive() || t.level()!=m.level()) return new Plan(false,null);
        if (canHitHere(m,t)) return new Plan(true,null);
        Cached cache = CACHE.get(m);
        if (cache!=null && cache.target.equals(t.getUUID()) && now<cache.until && cache.weapon==m.getMainHandItem().getItem() && cache.task==m.getTask()
                && cache.maid.distanceToSqr(m.position())<1 && cache.enemy.distanceToSqr(t.position())<1) return cache.plan;
        var s = Work.space(m);
        BlockPos base = Spaces.localPosition(m);
        boolean shooting = ranged(m);
        var candidates = new ArrayList<BlockPos>();
        if (Spaces.tracking(t)==s) {
            BlockPos target = BlockPos.containing(Spaces.local(s,t.position()));
            for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) candidates.add(target.offset(x,0,z));
        }
        // Bounded sample of possible firing/edge positions, never path to an external entity itself.
        int radius = Math.min(16,Math.max(2,(int)m.searchRadius()));
        for (int x=-radius;x<=radius;x+=2) for (int z=-radius;z<=radius;z+=2) for (int y=-1;y<=1;y++) {
            BlockPos p = base.offset(x,y,z);
            if (Work.blockAllowed(m,p) && !m.level().getBlockState(p.below()).getCollisionShape(m.level(),p.below()).isEmpty()) candidates.add(p);
        }
        candidates.sort(Comparator.comparingDouble(p -> Spaces.world(s,Vec3.atBottomCenterOf(p)).distanceToSqr(t.position())));
        Plan answer = new Plan(false,null);
        int paths=0;
        for (BlockPos p : candidates) {
            if (!Work.blockAllowed(m,p)) continue;
            Vec3 feet = Spaces.world(s,Vec3.atBottomCenterOf(p));
            double reach = shooting ? range(m) : m.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
            if (feet.distanceToSqr(t.position())>reach*reach) continue;
            if (m.level().clip(new ClipContext(feet.add(0,m.getEyeHeight(),0),t.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m)).getType()!=HitResult.Type.MISS) continue;
            if (++paths>12) break;
            if (Work.reachable(m,p,0)) { answer=new Plan(true,p.immutable()); break; }
        }
        CACHE.put(m,new Cached(t.getUUID(),now+20,m.position(),t.position(),m.getMainHandItem().getItem(),m.getTask(),answer));
        return answer;
    }
}
