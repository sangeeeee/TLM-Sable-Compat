package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Explicit boundary between block addresses in plots and entities in world coordinates. */
public final class Work {
    private Work() {}
    public static SubLevel space(EntityMaid maid) {
        if (maid.isHomeModeEnable()) {
            Binding b = Homes.binding(maid);
            return b == null || b.points.isEmpty() || !b.failure.isEmpty() ? null : Spaces.resolve(maid.level(), b.points.getFirst().structure());
        }
        var owner = maid.getOwner();
        if (owner != null && owner.level() == maid.level()) {
            var selected = Spaces.tracking(owner);
            if (selected != null) return selected;
        }
        var here = Spaces.tracking(maid);
        return here != null ? here : Navigation.waitingSpace(maid);
    }
    public static boolean active(EntityMaid m) {
        return !m.level().isClientSide() && (Spaces.tracking(m) != null || space(m) != null || m.isHomeModeEnable() && Homes.managed(m));
    }
    public static boolean ready(EntityMaid m) {
        SubLevel s = space(m);
        return s != null && Spaces.tracking(m) == s && Spaces.upright(s) && !Homes.suspended(m);
    }
    public static boolean blockAllowed(EntityMaid m, BlockPos pos) {
        if (!active(m)) return Sable.HELPER.getContaining(m.level(), pos) == null;
        SubLevel s = space(m);
        if (!ready(m) || !Spaces.belongs(m.level(), pos, s)) return false;
        if (m.isHomeModeEnable()) return Homes.within(m, pos);
        var owner = m.getOwner();
        Vec3 center = owner != null && Spaces.tracking(owner) == s ? Spaces.local(s, owner.position()) : Spaces.local(s, m.position());
        return pos.getCenter().distanceToSqr(center) <= m.getRestrictRadius() * m.getRestrictRadius();
    }
    public static double distance(EntityMaid m, Vec3 blockPoint) {
        BlockPos address = BlockPos.containing(blockPoint);
        if (!active(m) && Sable.HELPER.getContaining(m.level(), address) == null) return m.position().distanceToSqr(blockPoint);
        return blockAllowed(m, address) ? m.position().distanceToSqr(Spaces.project(m.level(), blockPoint)) : Double.POSITIVE_INFINITY;
    }
    public static boolean reachable(EntityMaid m, BlockPos pos, int tolerance) {
        if (!blockAllowed(m, pos)) return false;
        Path path = m.getNavigation().createPath(pos, tolerance);
        return path != null && path.canReach();
    }
    public static boolean entityReachable(EntityMaid m, Entity entity) {
        if (!active(m)) return Spaces.tracking(entity) == null;
        SubLevel s = space(m);
        return ready(m) && Spaces.tracking(entity) == s && blockAllowed(m, BlockPos.containing(Spaces.local(s, entity.position())))
                && reachable(m, BlockPos.containing(Spaces.local(s, entity.position())), 1);
    }
    public static Stream<PoiRecord> pois(PoiManager manager, Predicate<Holder<PoiType>> type, BlockPos center,
                                          int radius, PoiManager.Occupancy occupancy, EntityMaid maid) {
        if (!active(maid)) return manager.getInRange(type, center, radius, occupancy)
                .filter(p -> Sable.HELPER.getContaining(maid.level(), p.getPos()) == null);
        if (!ready(maid)) return Stream.empty();
        var level = (ServerLevel)maid.level();
        return PoiSearch.aroundBoth(center.getCenter(), radius, center.getCenter(), 0)
                .filter(c -> level.hasChunk(c.x,c.z))
                .flatMap(c -> manager.getInChunk(type,c,occupancy))
                .filter(p -> p.getPos().distSqr(center) <= (double)radius * radius)
                .filter(p -> blockAllowed(maid,p.getPos())).limit(64)
                .filter(p -> level.getBlockState(p.getPos()).getBlock() instanceof com.github.tartaricacid.touhoulittlemaid.block.BlockJoy
                        ? AddonWork.approach(maid,p.getPos(),3).allowed() : reachable(maid,p.getPos(),1));
    }
    public static AABB worldBox(SubLevel space, AABB local) {
        if (space == null) return local;
        Vec3 min = new Vec3(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        Vec3 max = new Vec3(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY);
        for (int i=0;i<8;i++) {
            Vec3 p = Spaces.world(space,new Vec3((i&1)==0?local.minX:local.maxX,(i&2)==0?local.minY:local.maxY,(i&4)==0?local.minZ:local.maxZ));
            min = new Vec3(Math.min(min.x,p.x),Math.min(min.y,p.y),Math.min(min.z,p.z));
            max = new Vec3(Math.max(max.x,p.x),Math.max(max.y,p.y),Math.max(max.z,p.z));
        }
        return new AABB(min,max);
    }
}
