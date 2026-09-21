package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemKappaCompass;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import java.util.UUID;

public final class Compasses {
    private Compasses() {}
    public static UUID id(ItemStack stack) {
        var t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return t.hasUUID(Homes.KEY) ? t.getUUID(Homes.KEY) : null;
    }
    private static void attach(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putUUID(Homes.KEY, id));
    }
    private static Binding read(ServerLevel level, ItemStack stack) {
        UUID id = id(stack);
        if (id != null) return BindingStore.get(level).find(id);
        // Adopt ordinary-world legacy compasses; ambiguous old plot addresses require re-recording.
        if (!ItemKappaCompass.hasKappaCompassData(stack)) return null;
        Binding b = new Binding(true);
        for (Activity a : new Activity[]{Activity.WORK, Activity.IDLE, Activity.REST}) {
            BlockPos p = ItemKappaCompass.getPoint(a, stack);
            if (p == null) continue;
            if (Sable.HELPER.isInPlotGrid(level, p.getX() >> 4, p.getZ() >> 4)) b.failure = "legacy";
            b.points.add(new Binding.Point(ItemKappaCompass.getDimension(stack).toString(), null, p, p));
            if (b.points.size() >= ItemKappaCompass.getRecordCount(stack)) break;
        }
        attach(stack, BindingStore.get(level).add(b));
        return b;
    }
    private static void sync(ItemStack stack, Binding b) {
        stack.remove(InitDataComponent.KAPPA_COMPASS_ACTIVITY_POS);
        Activity[] activities = {Activity.WORK, Activity.IDLE, Activity.REST};
        for (int i = 0; i < b.points.size(); i++) ItemKappaCompass.addPoint(activities[i], b.points.get(i).position(), stack);
        ItemKappaCompass.addDimension(net.minecraft.resources.ResourceLocation.parse(b.points.getFirst().dimension()), stack);
    }
    public static InteractionResult record(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        if (player.isDiscrete()) {
            stack.remove(InitDataComponent.KAPPA_COMPASS_ACTIVITY_POS);
            stack.remove(InitDataComponent.KAPPA_COMPASS_DIMENSION);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(Homes.KEY));
            Homes.tell(player, "compass_clear"); return InteractionResult.SUCCESS;
        }
        Binding b = read(level, stack);
        if (id(stack) != null && b == null) { Homes.tell(player, "missing"); return InteractionResult.CONSUME; }
        BlockPos pos = context.getClickedPos();
        var s = Sable.HELPER.getContaining(level, pos);
        var point = Spaces.point(level, s, pos, pos);
        if (b != null) {
            String error = Homes.status(level, b);
            if (!error.isEmpty()) { Homes.tell(player, error); return InteractionResult.CONSUME; }
            if (!b.points.getFirst().sameSpace(point)) {
                Homes.tell(player, "compass_space"); return InteractionResult.CONSUME;
            }
            if (b.points.size() >= 3) { Homes.tell(player, "compass_full"); return InteractionResult.CONSUME; }
            if (b.points.getLast().position().distSqr(pos) > 4096) {
                Homes.tell(player, "compass_far"); return InteractionResult.CONSUME;
            }
        }
        Binding next = b == null ? new Binding(true) : b.copy();
        next.points.add(point); next.touchedStructure |= s != null;
        // Copy-on-write: duplicated item stacks must not mutate each other's configuration.
        attach(stack, BindingStore.get(level).add(next));
        sync(stack, next);
        Homes.tell(player, "record_" + next.points.size());
        return InteractionResult.SUCCESS;
    }
    public static InteractionResult bind(ItemStack stack, Player player, EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!maid.isOwnedBy(player)) { Homes.tell(player, "owner"); return InteractionResult.CONSUME; }
        if (player.isDiscrete()) {
            if (maid.isHomeModeEnable() && (Homes.managed(maid) || Spaces.tracking(maid) != null)) {
                // Keep the previous configuration intact if a replacement Home cannot be made safely.
                if (!Homes.setSimple(maid)) return InteractionResult.CONSUME;
            } else {
                Binding old = Homes.binding(maid);
                maid.getPersistentData().remove(Homes.KEY);
                if (old != null && old.failure.isEmpty() && !old.points.isEmpty()) {
                    Binding simple = new Binding(false);
                    simple.points.add(old.points.getFirst());
                    simple.touchedStructure = old.touchedStructure;
                    Homes.attach(maid, BindingStore.get(level).add(simple));
                }
            }
            maid.getSchedulePos().clear(maid);
            Navigation.clearMovement(maid);
            Homes.tell(player, "maid_clear"); return InteractionResult.SUCCESS;
        }
        Binding b = read(level, stack);
        if (b == null) { Homes.tell(player, id(stack) == null ? "empty" : "missing"); return InteractionResult.CONSUME; }
        String error = Homes.status(level, b);
        if (error.isEmpty() && (b.touchedStructure || Spaces.tracking(maid) != null)) error = Homes.validateMaid(maid, b);
        if (!error.isEmpty()) { Homes.tell(player, error); return InteractionResult.CONSUME; }
        // Every check passed; only now mutate the maid and refresh the visible compass coordinates.
        Homes.attach(maid, BindingStore.get(level).add(b.copy()));
        maid.getSchedulePos().setConfigured(true);
        maid.getSchedulePos().restrictTo(maid);
        sync(stack, b);
        Homes.tell(player, "bound");
        return InteractionResult.SUCCESS;
    }
}
