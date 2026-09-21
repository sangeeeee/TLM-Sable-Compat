package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ride.MaidRideFindWaterTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindSitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidNearestLivingEntitySensor;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFishing;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public class FishingGameTests {
    private static void start(MaidRideFindWaterTask task,GameTestHelper h,EntityMaid maid) throws ReflectiveOperationException {
        WorkGameTests.invoke(task,"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),maid,0L);
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void oneWaterBlockFishingOnMovingStructure(GameTestHelper h) throws ReflectiveOperationException {
        var ship=CompatGameTests.platform(h,1);var maid=CompatGameTests.maid(h,ship);
        Homes.setSimple(maid);maid.setHomeModeEnable(true);maid.getSchedulePos().restrictTo(maid);
        maid.setTask(new TaskFishing());maid.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FISHING_ROD));
        BlockPos water=ship.getPlot().getCenterBlock().offset(5,1,3);
        h.getLevel().setBlock(water,Blocks.WATER.defaultBlockState(),2);
        // The original hook itself rejects a standing owner, independent of structure support.
        var standing=new MaidFishingHook(maid,h.getLevel(),0,0,maid.position());h.getLevel().addFreshEntity(standing);standing.tick();
        h.assertTrue(standing.isRemoved() && maid.fishing==null,"Original fishing requires a vehicle, not just a held rod");
        var chair=new EntityChair(h.getLevel());chair.setPos(maid.position());h.getLevel().addFreshEntity(chair);
        ((EntityMovementExtension)chair).sable$setTrackingSubLevel(ship);
        WorkGameTests.invoke(new MaidNearestLivingEntitySensor(),"doTick",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),maid);
        WorkGameTests.invoke(new MaidFindSitTask(0.6f),"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),maid,0L);
        h.assertTrue(maid.getVehicle()==chair,"Fishing must still discover and mount a reachable maid chair");
        var task=new MaidRideFindWaterTask(6,3);start(task,h,maid);start(task,h,maid);
        var hook=maid.fishing;
        h.assertTrue(hook!=null && !hook.isRemoved(),"One water block must be enough to cast after sitting");
        h.assertTrue(hook.position().distanceTo(Spaces.world(ship,water.getCenter()))<0.01,"Hook must spawn in world space exactly once");
        Vec3 localMaid=Spaces.local(ship,maid.position()),localChair=Spaces.local(ship,chair.position());
        for(int i=0;i<8;i++) hook.tick();
        var lure=MaidFishingHook.class.getDeclaredField("timeUntilLured");lure.setAccessible(true);
        h.assertTrue(lure.getInt(hook)>0,"Hook must detect local water and advance native bite timers");
        ship.logicalPose().position().add(80,0,40);ship.logicalPose().orientation().rotateY(0.8);
        maid.setPos(Spaces.world(ship,localMaid));chair.setPos(Spaces.world(ship,localChair));hook.tick();
        Vec3 localHook=Spaces.local(ship,hook.position());
        h.assertTrue(!hook.isRemoved() && Math.abs(localHook.x-water.getX()-0.5)<0.001 && Math.abs(localHook.z-water.getZ()-0.5)<0.001,"Floating hook must follow pool translation and yaw");
        var nibble=MaidFishingHook.class.getDeclaredField("nibble");nibble.setAccessible(true);nibble.setInt(hook,2);
        int before=h.getLevel().getEntitiesOfClass(ItemEntity.class,maid.getBoundingBox().inflate(3)).size();
        hook.tick();
        h.assertTrue(hook.isRemoved() && maid.fishing==null,"Native bite must retrieve the hook");
        h.assertTrue(!hook.isOpenWaterFishing(),"One-cell pool must not bypass the original open-water treasure requirement");
        h.assertTrue(maid.getMainHandItem().getDamageValue()>0 && h.getLevel().getEntitiesOfClass(ItemEntity.class,maid.getBoundingBox().inflate(3)).size()>before,"Native fishing must produce loot and wear the rod");
        start(task,h,maid);h.assertTrue(maid.fishing!=null,"Maid must be able to cast again");
        var next=maid.fishing;h.getLevel().setBlock(water,Blocks.AIR.defaultBlockState(),2);next.tick();
        h.assertTrue(next.isRemoved(),"Removing the pool must end fishing instead of fishing empty space");
        maid.discard();chair.discard();CompatGameTests.cleanup(h,ship);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void ordinaryFishingAndForeignPool(GameTestHelper h) throws ReflectiveOperationException {
        var a=CompatGameTests.platform(h,1);var b=CompatGameTests.platform(h,10);var maid=CompatGameTests.maid(h,a);
        Homes.setSimple(maid);maid.setHomeModeEnable(true);maid.getSchedulePos().restrictTo(maid);
        maid.setTask(new TaskFishing());maid.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FISHING_ROD));
        var chair=new EntityChair(h.getLevel());chair.setPos(maid.position());h.getLevel().addFreshEntity(chair);maid.startRiding(chair,true);
        var task=new MaidRideFindWaterTask(6,3);
        BlockPos foreign=b.getPlot().getCenterBlock().offset(3,1,3);h.getLevel().setBlock(foreign,Blocks.WATER.defaultBlockState(),2);
        start(task,h,maid);
        var waterField=MaidRideFindWaterTask.class.getDeclaredField("waterPos");waterField.setAccessible(true);waterField.set(task,foreign);
        start(task,h,maid);h.assertTrue(maid.fishing==null,"Another structure's cached pool must be rejected");
        maid.setHomeModeEnable(false);((EntityMovementExtension)maid).sable$setTrackingSubLevel(null);
        BlockPos world=h.absolutePos(new BlockPos(3,10,3));maid.setPos(Vec3.atBottomCenterOf(world));chair.setPos(maid.position());
        h.getLevel().setBlock(world.east(),Blocks.WATER.defaultBlockState(),2);task=new MaidRideFindWaterTask(6,3);
        start(task,h,maid);start(task,h,maid);
        h.assertTrue(maid.fishing!=null && !maid.fishing.getPersistentData().hasUUID(Resting.KEY),"Ordinary-world casting must keep the original hook behavior");
        maid.fishing.tick();h.assertTrue(!maid.fishing.isRemoved(),"Ordinary hook must remain valid in water");
        maid.fishing.discard();maid.discard();chair.discard();CompatGameTests.cleanup(h,a,b);h.succeed();
    }
}
