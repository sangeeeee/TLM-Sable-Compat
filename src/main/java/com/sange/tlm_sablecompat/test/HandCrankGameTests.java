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
