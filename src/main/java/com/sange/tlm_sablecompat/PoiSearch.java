package com.sange.tlm_sablecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.stream.Stream;

/** Enumerate two local search areas, never the (potentially enormous) gap between them. */
public final class PoiSearch {
    private PoiSearch() {}

    public static Stream<ChunkPos> aroundBoth(Vec3 center, int centerRadius, Vec3 maid, int maidRadius) {
        return Stream.concat(around(center, centerRadius), around(maid, maidRadius)).distinct();
    }

    private static Stream<ChunkPos> around(Vec3 position, int radius) {
        BlockPos center = BlockPos.containing(position);
        int r = Math.max(0, radius);
        return ChunkPos.rangeClosed(new ChunkPos(Math.floorDiv(center.getX() - r, 16), Math.floorDiv(center.getZ() - r, 16)),
                new ChunkPos(Math.floorDiv(center.getX() + r, 16), Math.floorDiv(center.getZ() + r, 16)));
    }
}
