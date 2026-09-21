package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.datagen.tag.TagBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import java.util.UUID;

public final class Spaces {
    private Spaces() {}
    public static SubLevel tracking(Entity entity) {
        SubLevel s = Sable.HELPER.getTrackingSubLevel(entity);
        return s != null && !s.isRemoved() ? s : null;
    }
    public static SubLevel resolve(Level level, UUID id) {
        var c = SubLevelContainer.getContainer(level);
        SubLevel s = c == null || id == null ? null : c.getSubLevel(id);
        return s != null && !s.isRemoved() ? s : null;
    }
    public static boolean upright(SubLevel s) {
        // First-stage navigation supports mild tilt, not wall/ceiling walking.
        return s == null || s.logicalPose().orientation().transform(new Vector3d(0, 1, 0)).y >= 0.985;
    }
    public static Vec3 local(SubLevel s, Vec3 world) { return s == null ? world : s.logicalPose().transformPositionInverse(world); }
    public static Vec3 world(SubLevel s, Vec3 local) { return s == null ? local : s.logicalPose().transformPosition(local); }
    public static BlockPos localPosition(Entity e) { return BlockPos.containing(local(tracking(e), e.position())); }
    public static Vec3 project(Level l, Vec3 p) { return Sable.HELPER.projectOutOfSubLevel(l, p); }
    public static boolean belongs(Level level, BlockPos p, SubLevel s) {
        return Sable.HELPER.getContaining(level, p) == s;
    }
    public static Binding.Point point(Level level, SubLevel s, BlockPos position, BlockPos anchor) {
        return new Binding.Point(level.dimension().location().toString(), s == null ? null : s.getUniqueId(), position, anchor);
    }
    public static BlockPos support(Level level, SubLevel s, Vec3 position) {
        BlockPos feet = BlockPos.containing(local(s, position));
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos p = feet.offset(0, dy, 0);
            if (belongs(level, p, s) && level.hasChunkAt(p) &&
                    !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return p;
        }
        return null;
    }
    /** Full target box check against the world AND every intersecting structure, using current poses. */
    public static boolean clear(EntityMaid maid, AABB box) {
        Level level = maid.level();
        if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(maid, box)) return false;
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return true;
        // Physics broad-phase bounds can lag a just-assembled/moved structure by one tick.
        // Landing is infrequent; inspect loaded structures using their current poses instead.
        for (SubLevel s : container.getAllSubLevels()) {
            if (s.isRemoved()) continue;
            BoundingBox3d local = new BoundingBox3d(box);
            local.transformInverse(s.logicalPose(), new Matrix4d(), local);
            AABB localBox = new AABB(local.minX, local.minY, local.minZ, local.maxX, local.maxY, local.maxZ);
            if (localBox.getSize() > 16) return false;
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(localBox.minX, localBox.minY - 1, localBox.minZ),
                    BlockPos.containing(localBox.maxX, localBox.maxY, localBox.maxZ))) {
                if (!belongs(level, p, s)) continue;
                for (AABB shape : level.getBlockState(p).getCollisionShape(level, p).toAabbs()) {
                    if (shape.move(p).intersects(localBox)) return false;
                }
            }
        }
        return true;
    }
    public static Vec3 landing(EntityMaid maid, SubLevel s, BlockPos feet) {
        Level level = maid.level();
        BlockPos floor = feet.below();
        if (!upright(s) || !belongs(level, feet, s) || !belongs(level, floor, s) || !level.hasChunkAt(floor)) return null;
        var state = level.getBlockState(floor);
        if (state.is(TagBlock.MAID_AVOID_BLOCK) || !state.isFaceSturdy(level, floor, Direction.UP)) return null;
        if (!level.getFluidState(feet).isEmpty()) return null;
        Vec3 target = world(s, Vec3.atBottomCenterOf(feet)).add(0, 0.06, 0);
        AABB box = maid.getBoundingBox().move(target.subtract(maid.position())).deflate(0.001);
        return clear(maid, box) ? target : null;
    }
    public static boolean teleportNear(EntityMaid maid, SubLevel s, Vec3 center, boolean avoidCenter) {
        if (s != null && (s.isRemoved() || !upright(s))) return false;
        BlockPos origin = BlockPos.containing(center);
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = maid.getRandom().nextInt(7) - 3, dz = maid.getRandom().nextInt(7) - 3;
            int dy = maid.getRandom().nextInt(3) - 1;
            if (avoidCenter && Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            Vec3 pos = landing(maid, s, origin.offset(dx, dy, dz));
            if (pos == null || (s != null && s.isRemoved())) continue;
            maid.moveTo(pos.x, pos.y, pos.z, maid.getYRot(), maid.getXRot());
            ((EntityMovementExtension)maid).sable$setTrackingSubLevel(s);
            maid.setDeltaMovement(Vec3.ZERO);
            Navigation.clearMovement(maid);
            maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
            return true;
        }
        return false;
    }
}
