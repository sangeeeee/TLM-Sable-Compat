package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.hmtl.tlmmaidmanager.compat.sable.SableCompat;
import com.hmtl.tlmmaidmanager.init.InitBlocks;
import com.hmtl.tlmmaidmanager.world.WorkBlockData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

/** Only loaded when Maid Manager is installed. Addresses are block addresses, never projections. */
public final class ManagerAnchors {
    private ManagerAnchors() {}

    public static void moved(ServerLevel source, dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform transform, Set<BlockPos> blocks) {
        ServerLevel target = transform.getLevel();
        WorkBlockData data = WorkBlockData.get(source);
        for (BlockPos oldPos : blocks) {
            BlockPos newPos = transform.apply(oldPos);
            if (!target.getBlockState(newPos).is(InitBlocks.WORK_BLOCK.get())) continue;
            String oldKey = key(source, oldPos), newKey = key(target, newPos);
            if (oldKey.equals(newKey)) continue;
            data.moveEntry(oldKey, newKey);
            data.onBlockRemoved(source, oldPos);
            SableCompat.onWorkBlockRemoved(source, oldPos);
            // A structure ticket replaces a world-chunk ticket; never force-load plot-grid chunks.
            boolean world = Sable.HELPER.getContaining(target, newPos) == null;
            data.onBlockPlaced(target, newPos, world, newPos);
            SableCompat.onWorkBlockPlaced(target, newPos);
        }
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static SableCompat.Result teleport(EntityMaid maid, ServerLevel level, BlockPos anchor) {
        // Never restore a removed/stored ship from Manager's cached serialization pointer.
        if (!level.hasChunkAt(anchor) || !level.getBlockState(anchor).is(InitBlocks.WORK_BLOCK.get()))
            return fail(maid, "manager_anchor_missing");
        var structure = Sable.HELPER.getContaining(level, anchor);
        if (structure == null && Sable.HELPER.isInPlotGrid(level, anchor))
            return fail(maid, "manager_anchor_missing");
        if (structure != null && (structure.isRemoved() || !Spaces.upright(structure)))
            return fail(maid, "tilted");

        // A dispatch must not silently destroy a compass schedule or immediately return to its old ship.
        Binding previous = Homes.binding(maid);
        boolean compass = maid.getSchedulePos().isConfigured();
        if (compass) {
            if (previous == null || !Homes.status(level, previous).isEmpty()
                    || !previous.points.getFirst().sameSpace(Spaces.point(level, structure, anchor, anchor)))
                return fail(maid, "manager_compass_conflict");
        }

        Vec3 destination = null;
        // Bounded, deterministic search: the anchor top first, then adjacent rings, no tick scanner.
        for (int radius = 0; radius <= 3 && destination == null; radius++) {
            for (int dy : new int[]{1, 0, 2, -1, 3}) {
                for (int dx = -radius; dx <= radius && destination == null; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        destination = Spaces.landing(maid, level, structure, anchor.offset(dx, dy, dz));
                        if (destination != null) break;
                    }
                }
                if (destination != null) break;
            }
        }
        if (destination == null) return fail(maid, "manager_no_landing");

        Resting.leave(maid);
        maid.stopRiding();
        if (maid.level() != level) {
            BlockPos arrival = BlockPos.containing(destination);
            level.getChunkAt(arrival);
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT,
                    new net.minecraft.world.level.ChunkPos(arrival), 1, maid.getId());
            if (!maid.teleportTo(level, destination.x, destination.y, destination.z, Set.of(), maid.getYRot(), maid.getXRot()))
                return fail(maid, "manager_transfer_failed");
            // Entity.teleportTo recreates non-player entities in the destination level.
            if (!(level.getEntity(maid.getUUID()) instanceof EntityMaid arrived))
                return fail(maid, "manager_transfer_failed");
            maid = arrived;
        } else maid.moveTo(destination.x, destination.y, destination.z, maid.getYRot(), maid.getXRot());
        ((EntityMovementExtension)maid).sable$setTrackingSubLevel(structure);
        ((LivingEntityMovementExtension)maid).sable$getInheritedVelocity().zero();
        EntitySubLevelUtil.setOldPosNoMovement(maid);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0;
        maid.setOnGround(true);
        Navigation.clearMovement(maid);
        Navigation.state(maid).resetTasks = true;
        Combat.afterTransfer(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS.get());
        if (!compass) {
            Binding home = new Binding(false);
            home.touchedStructure = structure != null;
            home.points.add(Spaces.point(level, structure, anchor.above(), anchor));
            Homes.attach(maid, BindingStore.get(level).add(home));
        } else Homes.sync(maid, previous);
        maid.setHomeModeEnable(true);
        maid.getSchedulePos().restrictTo(maid);
        return SableCompat.Result.ENTERED;
    }

    private static SableCompat.Result fail(EntityMaid maid, String reason) {
        Homes.notifyOwner(maid, reason, false);
        return SableCompat.Result.FAILED_PLOT;
    }

    public static boolean teleportForTask(EntityMaid maid, WorkBlockData.Entry entry) {
        if (!(maid.level() instanceof ServerLevel source)) return false;
        for (ServerLevel level : source.getServer().getAllLevels())
            if (level.dimension().location().toString().equals(entry.dimension()))
                return teleport(maid, level, new BlockPos(entry.x(), entry.y(), entry.z())) == SableCompat.Result.ENTERED;
        return false;
    }
}
