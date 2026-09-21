package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

public final class Resting {
    private Resting() {}
    public static final String KEY="tlm_sablecompat_attachment";
    public interface SeatData {
        void compat$associated(BlockPos pos);
    }
    private static Binding binding(Entity e) {
        return e.level() instanceof ServerLevel level && e.getPersistentData().hasUUID(KEY)
                ? BindingStore.get(level).find(e.getPersistentData().getUUID(KEY)) : null;
    }
    private static void attach(Entity e,BlockPos pos) {
        if (!(e.level() instanceof ServerLevel level)) return;
        var s=Sable.HELPER.getContaining(level,pos);
        if (s==null) return;
        detach(e);
        Binding b=new Binding(false); b.touchedStructure=true;
        b.points.add(Spaces.point(level,s,pos,pos));
        e.getPersistentData().putUUID(KEY,BindingStore.get(level).add(b));
    }
    /** Only bed/seat attachments are transient; compass/Home records retain their revocations. */
    public static void detach(Entity e) {
        if (e.level() instanceof ServerLevel level && e.getPersistentData().hasUUID(KEY))
            BindingStore.get(level).release(e.getPersistentData().getUUID(KEY));
        e.getPersistentData().remove(KEY);
    }
    public static void attachSleep(EntityMaid m,BlockPos pos) { attach(m,pos); }
    public static void leave(EntityMaid m) {
        if (m.isSleeping()) m.stopSleeping();
        if (m.getVehicle() instanceof EntitySit seat) {
            m.stopRiding();
            if (seat.getPassengers().isEmpty()) seat.discard();
        }
    }
    public static void tick(EntityMaid m) {
        if (m.level().isClientSide()) return;
        Binding b=binding(m);
        if (m.isSleeping() && b==null && m.getPersistentData().hasUUID(KEY)) { m.stopSleeping(); return; }
        if (m.isSleeping() && b!=null) {
            if (!Homes.status((ServerLevel)m.level(),b).isEmpty() || Homes.suspended(m)) { m.stopSleeping(); return; }
            BlockPos pos=b.points.getFirst().position();
            if (!m.level().getBlockState(pos).isBed(m.level(),pos,m)) { m.stopSleeping(); return; }
            m.setSleepingPos(pos);
        }
        if (Homes.suspended(m)) leave(m);
    }
    public static boolean sleep(EntityMaid m) {
        if (!m.isSleeping()) return false;
        BlockPos pos=m.getSleepingPos().orElseThrow();
        var s=Sable.HELPER.getContaining(m.level(),pos);
        if (s==null) return false;
        m.setPos(Spaces.world(s,Vec3.atLowerCornerOf(pos).add(0.5,0.5625,0.5)));
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(s);
        m.setDeltaMovement(Vec3.ZERO); m.setSilent(true);
        return true;
    }
    public static boolean wake(EntityMaid m) {
        Binding b=binding(m);
        BlockPos pos=b!=null && !b.points.isEmpty()?b.points.getFirst().position():m.getSleepingPos().orElse(null);
        var s=pos==null?null:Sable.HELPER.getContaining(m.level(),pos);
        if (s==null && !m.getPersistentData().hasUUID(KEY)) return false;
        if (pos!=null && m.level().hasChunkAt(pos) && (b==null || b.failure.isEmpty() && s==Spaces.resolve(m.level(),b.points.getFirst().structure()))) {
            var block=m.level().getBlockState(pos);
            if (block.isBed(m.level(),pos,m)) block.setBedOccupied(m.level(),pos,m,false);
        }
        m.clearSleepingPos(); m.setPose(Pose.STANDING); m.setSilent(false);
        detach(m);
        if (!m.level().isClientSide() && s!=null) Spaces.teleportNear(m,s,pos.above().getCenter(),false);
        return true;
    }
    public static float seatYaw(EntitySit seat) {
        var s=Sable.HELPER.getContaining(seat.level(),seat.position());
        if (s==null) return seat.getYRot();
        Vec3 direction=s.logicalPose().transformNormal(seat.getLookAngle());
        return (float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
    }
    public static void seat(EntitySit seat) {
        if (!seat.level().isClientSide()) {
            var level=(ServerLevel)seat.level();
            Binding b=binding(seat);
            if (b==null && !seat.getPersistentData().hasUUID(KEY)) {
                BlockPos anchor=seat.getAssociatedBlockPos();
                var s=Sable.HELPER.getContaining(level,anchor);
                if (s==null) return;
                attach(seat,anchor); b=binding(seat);
                // Retained seats live in plot coordinates. Sable projects their passengers/rendering.
                Vec3 offset=seat.position().subtract(Vec3.atLowerCornerOf(anchor));
                seat.getPersistentData().putDouble("SeatX",offset.x); seat.getPersistentData().putDouble("SeatY",offset.y); seat.getPersistentData().putDouble("SeatZ",offset.z);
            }
            String error=b==null ? "missing" : Homes.status(level,b);
            boolean maidPassenger=seat.getPassengers().stream().anyMatch(e->e instanceof EntityMaid);
            if (!error.isEmpty() && !(error.equals("tilted") && !maidPassenger)) {
                seat.ejectPassengers(); seat.discard(); return;
            }
            BlockPos pos=b.points.getFirst().position();
            // Block replacement is a lifecycle change even when the replacement has collision.
            var block=level.getBlockState(pos).getBlock();
            if (!(block instanceof com.github.tartaricacid.touhoulittlemaid.block.BlockJoy)
                    && !(block instanceof com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameBlock)) {
                seat.ejectPassengers(); seat.discard(); return;
            }
            var tag=seat.getPersistentData();
            ((SeatData)seat).compat$associated(pos);
            seat.setPos(Vec3.atLowerCornerOf(pos).add(tag.getDouble("SeatX"),tag.getDouble("SeatY"),tag.getDouble("SeatZ")));
            var s=Spaces.resolve(level,b.points.getFirst().structure());
            for (Entity passenger:seat.getPassengers()) ((EntityMovementExtension)passenger).sable$setTrackingSubLevel(s);
        }
    }
}
