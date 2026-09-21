package com.sange.tlm_sablecompat.test;

import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.stream.Stream;

/** Registered only when the real optional MUHC/Create dependencies are present. */
@PrefixGameTestTemplate(false)
public class HandCrankGameTests {
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void rejectForeignAndUnreachableCranks(GameTestHelper h) {
        var a=CompatGameTests.platform(h,1);var b=CompatGameTests.platform(h,10);var maid=CompatGameTests.maid(h,a);
        Homes.setSimple(maid);maid.setHomeModeEnable(true);maid.getSchedulePos().restrictTo(maid);
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","hand_crank"));
        BlockPos foreign=b.getPlot().getCenterBlock().offset(3,1,3);
        h.getLevel().setBlock(foreign,block.defaultBlockState(),2);
        h.assertTrue(!Cranks.approach(maid,foreign).allowed(),"Home maid must not operate another structure's crank");
        BlockPos isolated=a.getPlot().getCenterBlock().offset(12,1,3);
        h.getLevel().setBlock(isolated.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(isolated,block.defaultBlockState(),2);
        h.assertTrue(Work.blockAllowed(maid,isolated) && !Cranks.approach(maid,isolated).allowed(),"In-area crank without any reachable interaction position must be rejected");
        maid.setHomeModeEnable(false);
        ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)maid).sable$setTrackingSubLevel(null);
        h.assertTrue(!Cranks.approach(maid,foreign).allowed(),"Ordinary-world maid must not select a structure crank");
        maid.discard();CompatGameTests.cleanup(h,a,b);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void elevatedCrankAndLocalApproach(GameTestHelper h) throws ReflectiveOperationException {
        var ship=CompatGameTests.platform(h,1);var maid=CompatGameTests.maid(h,ship);
        Homes.setSimple(maid);maid.setHomeModeEnable(true);maid.getSchedulePos().restrictTo(maid);
        var base=ship.getPlot().getCenterBlock();var crank=base.offset(3,4,3);
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","hand_crank"));
        h.getLevel().setBlock(crank.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(crank,block.defaultBlockState(),2);
        h.assertTrue(!Work.reachable(maid,crank,1),"Regression setup: raised crank fails the old one-block path filter");
        var type=Class.forName("com.sch246.muhc.maid.task.UseHandCrank");var task=type.getConstructor(float.class).newInstance(0.5f);
        h.assertTrue(crank.equals(WorkGameTests.invoke(task,"findCrankHandle",new Class[]{com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,ServerLevel.class},maid,h.getLevel())),"Within-reach raised crank must be selected without walking onto it");
        var crankField=type.getDeclaredField("crankPos");crankField.setAccessible(true);crankField.set(task,crank);
        WorkGameTests.invoke(task,"tick",new Class[]{ServerLevel.class,com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,long.class},h.getLevel(),maid,0L);
        h.assertTrue(((com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity)h.getLevel().getBlockEntity(crank)).inUse>0,"Actual operation must rotate the elevated crank");
        h.getLevel().setBlock(crank,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        h.getLevel().setBlock(crank.below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        BlockPos far=base.offset(6,3,3);h.getLevel().setBlock(far,block.defaultBlockState(),2);
        maid.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(base.offset(0,1,3))));
        task=type.getConstructor(float.class).newInstance(0.5f);
        WorkGameTests.invoke(task,"checkExtraStartConditions",new Class[]{ServerLevel.class,com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class},h.getLevel(),maid);
        var walk=maid.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).orElseThrow();
        BlockPos stand=walk.getTarget().currentBlockPosition();
        h.assertTrue(Spaces.belongs(h.getLevel(),stand,ship) && Work.reachable(maid,stand,0),"MUHC must request a reachable local standing point, not a rounded world coordinate");
        maid.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(stand)));
        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        crankField.set(task,far);
        WorkGameTests.invoke(task,"tick",new Class[]{ServerLevel.class,com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,long.class},h.getLevel(),maid,1L);
        h.assertTrue(((com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity)h.getLevel().getBlockEntity(far)).inUse>0,"Crank must operate after reaching the chosen standing point");
        maid.discard();CompatGameTests.cleanup(h,ship);h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty", timeoutTicks = 100)
    public static void localHomeCrankSearch(GameTestHelper h) throws ReflectiveOperationException {
        var ship = CompatGameTests.platform(h,1);
        var maid = CompatGameTests.maid(h,ship);
        BlockPos crank = ship.getPlot().getCenterBlock().offset(4,1,3);
        var crankBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","hand_crank"));
        h.getLevel().setBlockAndUpdate(crank,crankBlock.defaultBlockState());
        maid.getSchedulePos().setHomeModeEnable(maid,maid.blockPosition());
        maid.setHomeModeEnable(true); maid.getSchedulePos().restrictTo(maid);
        Vec3 localMaid = Spaces.local(ship,maid.position());
        ship.logicalPose().position().add(40,0,20);
        maid.setPos(Spaces.world(ship,localMaid));

        var type = Class.forName("com.sch246.muhc.maid.task.UseHandCrank");
        Object task = type.getConstructor(float.class).newInstance(0.5f);
        var query = type.getMethod("getCrankInDoubleCircleUnion",ServerLevel.class,Vec3.class,int.class,Vec3.class,int.class);
        // Exclude this crank from the maid sphere while keeping its ship in MUHC's candidate range.
        // Only the correctly projected Home sphere may accept it.
        try (Stream<?> result = (Stream<?>)query.invoke(task,h.getLevel(),maid.getRestrictCenter().getCenter(),11,maid.position().add(30,0,30),4)) {
            h.assertTrue(result.map(p->((PoiRecord)p).getPos()).anyMatch(crank::equals),"MUHC must find a crank inside projected Home, independently of the maid sphere");
        }
        var find = type.getDeclaredMethod("findCrankHandle",com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,ServerLevel.class);
        find.setAccessible(true);
        h.assertTrue(crank.equals(find.invoke(task,maid,h.getLevel())),"Full MUHC findCrankHandle must complete with a local Home");
        // Ordinary-world callers of MUHC's public query keep their world center unchanged.
        BlockPos worldCrank = h.absolutePos(new BlockPos(10,3,3));
        h.getLevel().setBlockAndUpdate(worldCrank,crankBlock.defaultBlockState());
        try (Stream<?> result = (Stream<?>)query.invoke(task,h.getLevel(),worldCrank.getCenter(),4,worldCrank.getCenter(),4)) {
            h.assertTrue(result.map(p->((PoiRecord)p).getPos()).anyMatch(worldCrank::equals),"Normal world hand-crank search must continue working");
        }
        maid.discard(); CompatGameTests.cleanup(h,ship); h.succeed();
    }
}
