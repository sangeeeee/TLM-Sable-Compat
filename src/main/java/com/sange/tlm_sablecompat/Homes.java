package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class Homes {
    public static final String KEY = "tlm_sablecompat_binding";
    private Homes() {}
    public static UUID id(EntityMaid maid) {
        var tag = maid.getPersistentData(); return tag.hasUUID(KEY) ? tag.getUUID(KEY) : null;
    }
    public static Binding binding(EntityMaid maid) {
        return maid.level() instanceof ServerLevel l ? BindingStore.get(l).find(id(maid)) : null;
    }
    public static boolean managed(EntityMaid maid) {
        Binding b = binding(maid);
        return b != null && (b.touchedStructure || !b.failure.isEmpty()) || id(maid) != null && b == null && !maid.level().isClientSide();
    }
    public static void attach(EntityMaid maid, UUID id) {
        maid.getPersistentData().putUUID(KEY, id);
        Navigation.state(maid).notified = "";
        sync(maid, binding(maid));
        Navigation.clearMovement(maid);
    }
    public static int activity(EntityMaid maid) {
        Activity a = maid.getScheduleDetail(); return a == Activity.REST ? 2 : a == Activity.IDLE ? 1 : 0;
    }
    public static void sync(EntityMaid maid, Binding b) {
        if (b == null || b.points.isEmpty() || !b.failure.isEmpty()) return;
        SchedulePos schedule = maid.getSchedulePos();
        schedule.setWorkPos(b.activity(0).position());
        // Original compass fallback: REST uses IDLE if supplied, otherwise WORK.
        schedule.setIdlePos(b.activity(1).position());
        schedule.setSleepPos(b.activity(2).position());
        schedule.setDimension(net.minecraft.resources.ResourceLocation.parse(b.activity(0).dimension()));
    }
    public static String status(ServerLevel level, Binding b) {
        if (b == null) return "missing";
        if (!b.failure.isEmpty()) return b.failure;
        String spaces = b.validateSpaces();
        if (!spaces.isEmpty()) return spaces;
        for (Binding.Point p : b.points) {
            if (!p.dimension().equals(level.dimension().location().toString())) return "dimension";
            SubLevel s = Spaces.resolve(level, p.structure());
            if (p.structure() != null && s == null) return "unloaded";
            if (!level.hasChunkAt(p.anchor())) return "unloaded";
            if (p.structure() != null && Sable.HELPER.getContaining(level, p.anchor()) != s) return "missing";
            if (b.touchedStructure && level.getBlockState(p.anchor()).getCollisionShape(level, p.anchor()).isEmpty()) {
                if (b.compass) { b.failure = "anchor"; BindingStore.get(level).changed(); }
                return "anchor";
            }
            if (!Spaces.upright(s)) return "tilted";
        }
        return "";
    }
    public static String validateMaid(EntityMaid maid, Binding b) {
        String error = status((ServerLevel)maid.level(), b);
        if (!error.isEmpty()) return error;
        Binding.Point first = b.points.getFirst();
        SubLevel expected = Spaces.resolve(maid.level(), first.structure());
        SubLevel actual = Spaces.tracking(maid);
        if (expected != actual) return actual == null ? "not_on_structure" : "other_structure";
        Vec3 local = Spaces.local(expected, maid.position());
        double nearest = b.points.stream().mapToDouble(p -> p.position().getCenter().distanceToSqr(local)).min().orElse(Double.MAX_VALUE);
        return nearest > 32 * 32 ? "far" : "";
    }
    public static void tell(Player p, String key) { p.displayClientMessage(Component.translatable("message.tlm_sablecompat." + key), false); }
    public static void notifyOwner(EntityMaid maid, String failure, boolean force) {
        var state = Navigation.state(maid);
        if (!failure.isEmpty() && (force || !failure.equals(state.notified)) && maid.getOwner() instanceof Player p) {
            tell(p, failure); state.notified = failure;
        }
        if (failure.isEmpty()) state.notified = "";
    }
    public static boolean setSimple(EntityMaid maid) {
        SubLevel s = Spaces.tracking(maid);
        BlockPos pos = BlockPos.containing(Spaces.local(s, maid.position()));
        BlockPos anchor = Spaces.support(maid.level(), s, maid.position());
        if (s != null && (anchor == null || !Spaces.upright(s))) {
            notifyOwner(maid, anchor == null ? "unstable" : "tilted", true); return false;
        }
        if (anchor == null) anchor = pos.below();
        Binding b = new Binding(false);
        b.touchedStructure = s != null;
        b.points.add(Spaces.point(maid.level(), s, pos, anchor));
        attach(maid, BindingStore.get((ServerLevel)maid.level()).add(b));
        return true;
    }
    /** Called by SchedulePos.tick; returns whether this replaces the vanilla Home loop. */
    public static boolean tick(EntityMaid maid) {
        Binding b = binding(maid);
        sync(maid, b); // Also follows assembly of previously ordinary-world anchors.
        if (!managed(maid) || !maid.isHomeModeEnable()) return false;
        String error = status((ServerLevel)maid.level(), b);
        notifyOwner(maid, error, false);
        var state = Navigation.state(maid);
        if (!error.isEmpty()) {
            if (!state.suspended) {
                Navigation.clearMovement(maid);
                maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            }
            state.suspended = true; return true;
        }
        state.suspended = false;
        maid.getSchedulePos().restrictTo(maid);
        if (!maid.canBrainMoving() || maid.tickCount % 40 != 0) return true;
        Binding.Point target = b.activity(activity(maid));
        SubLevel s = Spaces.resolve(maid.level(), target.structure());
        Vec3 local = Spaces.local(s, maid.position());
        double distance = target.position().getCenter().distanceTo(local);
        if (Spaces.tracking(maid) == s && distance < maid.getRestrictRadius()) return true;
        if (Spaces.tracking(maid) != s || distance > maid.getRestrictRadius() + 4) {
            Spaces.teleportNear(maid, s, target.position().getCenter(), false);
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(maid, target.position(), 0.7f, 3);
        }
        return true;
    }
    public static boolean suspended(EntityMaid maid) {
        if (!maid.isHomeModeEnable() || !managed(maid)) return false;
        return !status((ServerLevel)maid.level(), binding(maid)).isEmpty();
    }
    public static boolean away(EntityMaid maid) {
        if (!maid.isHomeModeEnable() || !managed(maid)) return false;
        Binding b = binding(maid);
        return b != null && !b.points.isEmpty() && Spaces.tracking(maid) != Spaces.resolve(maid.level(), b.points.getFirst().structure());
    }
    public static boolean within(EntityMaid maid, BlockPos pos) {
        Binding b = binding(maid);
        if (b == null || !status((ServerLevel)maid.level(), b).isEmpty()) return false;
        Binding.Point point = b.activity(activity(maid));
        SubLevel home = Spaces.resolve(maid.level(), point.structure());
        SubLevel address = Sable.HELPER.getContaining(maid.level(), pos);
        if (address != null && address != home) return false;
        if (address == null && home != null) {
            if (Spaces.tracking(maid) != home) return false;
            pos = BlockPos.containing(Spaces.local(home, pos.getCenter()));
        }
        return Spaces.belongs(maid.level(), pos, home) && point.position().distSqr(pos) < maid.getRestrictRadius() * maid.getRestrictRadius();
    }
}
