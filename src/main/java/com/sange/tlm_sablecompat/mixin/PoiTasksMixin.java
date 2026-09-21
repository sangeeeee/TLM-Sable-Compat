package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(value={MaidBedTask.class,MaidJoyTask.class,MaidBoardGameTask.class,MaidCollectHoneyTask.class,MaidFindHomeMealTask.class},remap=false)
public class PoiTasksMixin {
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;position()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 localDistancePosition(EntityMaid m) { return Spaces.local(Spaces.tracking(m),m.position()); }
    @Redirect(method="*",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos localSortPosition(EntityMaid m) { return Spaces.localPosition(m); }
    @Redirect(method={"findBed","findJoy","findGameBlock","findBeehive","findPicnicMat"},require=1,at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getInRange(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"))
    private Stream<PoiRecord> reachablePois(PoiManager manager, Predicate<Holder<PoiType>> type, BlockPos center, int radius,
                                           PoiManager.Occupancy occupancy, ServerLevel level, EntityMaid maid) {
        return Work.pois(manager,type,center,radius,occupancy,maid);
    }
}
