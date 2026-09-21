package com.sange.tlm_sablecompat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World ledger also updates bindings belonging to unloaded maids and stored compasses. */
public final class BindingStore extends SavedData {
    private final Map<UUID, Binding> bindings = new HashMap<>();
    private boolean needsValidation = true;
    public static BindingStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(BindingStore::new, BindingStore::load), "tlm_sablecompat_bindings");
    }
    public UUID add(Binding b) {
        UUID id = UUID.randomUUID(); bindings.put(id, b); needsValidation = true; setDirty(); return id;
    }
    public Binding find(UUID id) { return id == null ? null : bindings.get(id); }
    public void release(UUID id) { if (bindings.remove(id) != null) setDirty(); }
    public void changed() { setDirty(); }
    public void removed(String dimension, UUID structure) {
        for (Binding b : bindings.values()) {
            if (!b.failure.isEmpty()) continue;
            if (b.points.stream().anyMatch(p -> p.dimension().equals(dimension) && structure.equals(p.structure()))) {
                b.failure = "removed"; setDirty();
            }
        }
    }
    /** Call after an actual block move, using exact membership rather than bounding boxes. */
    public void moved(ServerLevel source, AssemblyTransform transform, Set<BlockPos> blocks) {
        String from = source.dimension().location().toString();
        ServerLevel dest = transform.getLevel();
        for (Binding b : bindings.values()) {
            if (!b.failure.isEmpty()) continue;
            for (int i = 0; i < b.points.size(); i++) {
                Binding.Point p = b.points.get(i);
                if (!p.dimension().equals(from) || !blocks.contains(p.anchor())) continue;
                BlockPos anchor = transform.apply(p.anchor());
                var structure = Sable.HELPER.getContaining(dest, anchor);
                b.points.set(i, new Binding.Point(dest.dimension().location().toString(),
                        structure == null ? null : structure.getUniqueId(), transform.apply(p.position()), anchor));
                b.touchedStructure |= structure != null || p.structure() != null;
                needsValidation = true;
                setDirty();
            }
        }
    }
    /** Validate after the complete level tick, not halfway through a multi-fragment split. */
    public void settle() {
        if (!needsValidation) return;
        needsValidation = false;
        for (Binding b : bindings.values()) {
            if (!b.failure.isEmpty()) continue;
            String failure = b.validateSpaces();
            if (!failure.isEmpty()) { b.failure = failure; setDirty(); }
        }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        bindings.forEach((id, b) -> { CompoundTag t = b.save(); t.putUUID("Id", id); list.add(t); });
        tag.put("Bindings", list); return tag;
    }
    private static BindingStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BindingStore s = new BindingStore();
        for (Tag entry : tag.getList("Bindings", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag)entry;
            if (t.hasUUID("Id")) s.bindings.put(t.getUUID("Id"), Binding.load(t));
        }
        return s;
    }
}
