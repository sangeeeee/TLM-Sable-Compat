package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidPickupEntitiesSensor;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public class PickupGameTests {
    private static void collect(EntityMaid m) throws ReflectiveOperationException {
        WorkGameTests.invoke(m,"pushEntities",new Class[]{});
    }
    private static ItemEntity item(GameTestHelper h,EntityMaid m,Vec3 pos) {
        var item=new ItemEntity(h.getLevel(),pos.x,pos.y,pos.z,new ItemStack(Items.DIAMOND,3));
        item.setNoPickUpDelay();item.setNoGravity(true);h.getLevel().addFreshEntity(item);
        ((EntityMovementExtension)item).sable$setTrackingSubLevel(Spaces.tracking(m));return item;
    }
    private static void raised(GameTestHelper h,boolean structure) throws ReflectiveOperationException {
        var s=structure ? CompatGameTests.platform(h,1) : null;
        var m=structure ? CompatGameTests.maid(h,s) : new EntityMaid(h.getLevel());
        BlockPos feet=structure ? s.getPlot().getCenterBlock().offset(1,1,3) : h.absolutePos(new BlockPos(2,3,4));
        if(!structure) {
            for(BlockPos p:BlockPos.betweenClosed(feet.offset(-1,-1,-2),feet.offset(5,-1,2))) h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
            h.getLevel().addFreshEntity(m);
        } else {
            s.logicalPose().orientation().rotateY(.7).rotateX(.06);
        }
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(feet)));m.setOnGround(true);
        m.tame(CompatGameTests.player(h));m.setPickup(true);
        Homes.setSimple(m);m.setHomeModeEnable(true);m.getSchedulePos().restrictTo(m);
        BlockPos counter=feet.east(3);h.getLevel().setBlock(counter,Blocks.STONE.defaultBlockState(),2);
        // Too little headroom to stand on the counter: pickup must use the adjacent lower floor.
        h.getLevel().setBlock(counter.above(2),Blocks.STONE.defaultBlockState(),2);
        var item=item(h,m,Spaces.world(s,Vec3.atBottomCenterOf(counter.above()).add(0,.02,0)));
        WorkGameTests.invoke(new MaidPickupEntitiesSensor(),"doTick",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m);
        h.assertTrue(m.getBrain().getMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get()).orElseThrow().contains(item),"Sensor must include the raised item, including structure Home coordinates");
        WorkGameTests.invoke(new MaidPickupEntitiesTask(.6f),"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),m,0L);
        var destination=m.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().getTarget().currentBlockPosition();
        h.assertTrue(destination.getY()==feet.getY() && Work.reachable(m,destination,0),"Pickup must choose a reachable lower standing point");
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(destination)));collect(m);
        h.assertTrue(item.isRemoved(),"Actual pickup must collect one-block-high item without jumping onto the counter");
        int count=0;for(int i=0;i<m.getMaidInv().getSlots();i++) if(m.getMaidInv().getStackInSlot(i).is(Items.DIAMOND)) count+=m.getMaidInv().getStackInSlot(i).getCount();
        h.assertTrue(count==3,"Real inventory must receive the stack exactly once");
        m.discard();if(structure) CompatGameTests.cleanup(h,s);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="pickup")
    public static void raisedPickupInWorld(GameTestHelper h) throws ReflectiveOperationException { raised(h,false); }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="pickup")
    public static void raisedPickupOnStructure(GameTestHelper h) throws ReflectiveOperationException { raised(h,true); }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="pickup")
    public static void pickupRespectsDelaySwitchAndFloor(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);
        m.tame(CompatGameTests.player(h));m.setPickup(true);Homes.setSimple(m);m.setHomeModeEnable(true);m.getSchedulePos().restrictTo(m);
        Vec3 feet=AddonWork.position(m);
        var item=item(h,m,Spaces.world(s,feet.add(.6,1.02,0)));
        item.setPickUpDelay(20);collect(m);h.assertTrue(!item.isRemoved(),"Pickup delay must remain effective");
        item.setNoPickUpDelay();m.setPickup(false);collect(m);h.assertTrue(!item.isRemoved(),"Pickup switch must remain effective");m.setPickup(true);
        var inventory=m.getAvailableInv(false);
        for(int i=0;i<inventory.getSlots();i++) inventory.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));
        collect(m);h.assertTrue(!item.isRemoved(),"Full inventory must not delete a drop");
        for(int i=0;i<inventory.getSlots();i++) inventory.setStackInSlot(i,ItemStack.EMPTY);
        // An upper slab between the maid's eyes and the item blocks pickup even inside the enlarged vicinity.
        BlockPos slab=BlockPos.containing(feet).above();
        h.getLevel().setBlock(slab,Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP),2);
        item.setPos(Spaces.world(s,feet.add(0,2.02,0)));collect(m);
        h.assertTrue(!Pickup.visible(m,item,feet),"Slab must obstruct the pickup ray");
        h.assertTrue(!item.isRemoved(),"Items must not be picked through a slab floor");
        h.getLevel().setBlock(slab,Blocks.AIR.defaultBlockState(),2);
        // Placing a slab through the head lets Sable push the maid sideways immediately.
        // Reset the explicit test pose before testing coordinate-space filtering at the original distance.
        m.setPos(Spaces.world(s,feet));
        item.setPos(Spaces.world(s,feet.add(.6,1.02,0)));
        ((EntityMovementExtension)item).sable$setTrackingSubLevel(null);collect(m);
        h.assertTrue(!item.isRemoved(),"A nearby item in another coordinate space must not be collected");
        ((EntityMovementExtension)item).sable$setTrackingSubLevel(s);collect(m);
        h.assertTrue(item.isRemoved(),"Removing restrictions must allow real pickup");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
}

