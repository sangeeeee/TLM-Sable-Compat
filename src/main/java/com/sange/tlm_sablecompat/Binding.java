package com.sange.tlm_sablecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

/** Persistent addresses use plot coordinates, never cached world projections. */
public final class Binding {
    public record Point(String dimension, UUID structure, BlockPos position, BlockPos anchor) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Dimension", dimension);
            if (structure != null) t.putUUID("Structure", structure);
            t.putLong("Position", position.asLong());
            t.putLong("Anchor", anchor.asLong());
            return t;
        }
        public static Point load(CompoundTag t) {
            return new Point(t.getString("Dimension"), t.hasUUID("Structure") ? t.getUUID("Structure") : null,
                    BlockPos.of(t.getLong("Position")), BlockPos.of(t.getLong("Anchor")));
        }
        public boolean sameSpace(Point other) {
            return dimension.equals(other.dimension) && Objects.equals(structure, other.structure);
        }
    }

    public final List<Point> points = new ArrayList<>();
    public boolean compass;
    public boolean touchedStructure;
    /** Empty means valid. Invalidation is permanent, including after UUID reuse. */
    public String failure = "";

    public Binding(boolean compass) { this.compass = compass; }
    public Point activity(int index) { return points.get(Math.min(index, points.size() - 1)); }
    public String validateSpaces() {
        if (points.isEmpty()) return "empty";
        Point first = points.getFirst();
        for (Point p : points) if (!first.sameSpace(p)) return "split";
        return "";
    }
    public Binding copy() {
        Binding b = new Binding(compass);
        b.points.addAll(points);
        b.touchedStructure = touchedStructure;
        b.failure = failure;
        return b;
    }
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Compass", compass);
        t.putBoolean("TouchedStructure", touchedStructure);
        t.putString("Failure", failure);
        ListTag list = new ListTag();
        points.forEach(p -> list.add(p.save()));
        t.put("Points", list);
        return t;
    }
    public static Binding load(CompoundTag t) {
        Binding b = new Binding(t.getBoolean("Compass"));
        b.touchedStructure = t.getBoolean("TouchedStructure");
        b.failure = t.getString("Failure");
        for (Tag p : t.getList("Points", Tag.TAG_COMPOUND)) b.points.add(Point.load((CompoundTag)p));
        return b;
    }
}
