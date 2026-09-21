package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.*;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidNearestLivingEntitySensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.task.*;
import com.github.tartaricacid.touhoulittlemaid.init.*;
import com.github.tartaricacid.touhoulittlemaid.block.*;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
public class WorkGameTests {
    static Object invoke(Object obj,String method,Class<?>[] signature,Object...args) throws ReflectiveOperationException {
        for (Class<?> type=obj.getClass(); type!=null; type=type.getSuperclass()) {
            try {
                var m=type.getDeclaredMethod(method,signature);m.setAccessible(true);return m.invoke(obj,args);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(method);
    }
    private static void home(EntityMaid m) {
        Homes.setSimple(m);m.setHomeModeEnable(true);m.getSchedulePos().restrictTo(m);
    }
    private static BlockPos bed(GameTestHelper h,BlockPos foot) {
        var block=InitBlocks.MAID_BED.get().defaultBlockState().setValue(BlockMaidBed.FACING,Direction.EAST);
        h.getLevel().setBlock(foot,block.setValue(BlockMaidBed.PART,BedPart.FOOT),2);
        h.getLevel().setBlock(foot.east(),block.setValue(BlockMaidBed.PART,BedPart.HEAD),2);
        return foot.east();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void blockArrivalAndHarvest(GameTestHelper h) throws ReflectiveOperationException {
        var ship=CompatGameTests.platform(h,1); var m=CompatGameTests.maid(h,ship);home(m);
        var origin=ship.getPlot().getCenterBlock();BlockPos field=origin.offset(4,0,3);
        h.getLevel().setBlockAndUpdate(field,Blocks.FARMLAND.defaultBlockState());
        h.getLevel().setBlockAndUpdate(field.above(),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        var farm=new TaskNormalFarm();m.setTask(farm);
        // Mature wheat can randomly drop no seeds; this test checks replanting, not loot probability.
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.WHEAT_SEEDS));
        m.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(field.above())));
        m.getBrain().setMemory(InitEntities.TARGET_POS.get(),new BlockPosTracker(field));
        var action=new MaidFarmPlantTask(farm);
        h.assertTrue(action.tryStart(h.getLevel(),m,h.getLevel().getGameTime()),"Farm action must recognize local target distance");
        h.assertTrue(h.getLevel().getBlockState(field.above()).getValue(CropBlock.AGE)==0,"Actual crop harvesting must complete on the structure");
        AtomicBoolean interacted=new AtomicBoolean();
        m.getBrain().setMemory(InitEntities.TARGET_POS.get(),new BlockPosTracker(field));
        var common=new MaidArriveAtBlockTask(3,(maid,pos)->interacted.set(pos.equals(field)));
        h.assertTrue(common.tryStart(h.getLevel(),m,h.getLevel().getGameTime()) && interacted.get(),"Public block arrival callback must receive the original block address");
        m.discard();CompatGameTests.cleanup(h,ship);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void workSelectionAndUnreachableBlocks(GameTestHelper h) {
        var a=CompatGameTests.platform(h,1);var b=CompatGameTests.platform(h,10);var m=CompatGameTests.maid(h,a);
        var owner=CompatGameTests.player(h);m.tame(owner);CompatGameTests.OWNERS.put(m,owner);
        ((EntityMovementExtension)owner).sable$setTrackingSubLevel(b);owner.setPos(Spaces.world(b,b.getPlot().getCenterBlock().above().getCenter()));
        h.assertTrue(!Work.blockAllowed(m,a.getPlot().getCenterBlock()) && !Work.blockAllowed(m,b.getPlot().getCenterBlock()),"Maid must transfer to owner's structure before selecting work");
        home(m);
        h.assertTrue(Work.blockAllowed(m,a.getPlot().getCenterBlock()) && !Work.blockAllowed(m,b.getPlot().getCenterBlock()),"Home must take priority over owner's selected structure");
        BlockPos isolated=a.getPlot().getCenterBlock().offset(12,0,3);h.getLevel().setBlockAndUpdate(isolated,Blocks.STONE.defaultBlockState());
        boolean reachable=Work.reachable(m,isolated.above(),0);
        if (reachable) {
            var path=m.getNavigation().createPath(isolated.above(),0);
            TLMSableCompat.LOGGER.error("Unexpected island route: {}",path==null ? "null" : java.util.stream.IntStream.range(0,path.getNodeCount()).mapToObj(i->path.getNode(i).asBlockPos()+" floor="+h.getLevel().getBlockState(path.getNode(i).asBlockPos().below())).toList());
        }
        h.assertTrue(!reachable,"Disconnected platform without a route must not count as reachable");
        m.discard();CompatGameTests.cleanup(h,a,b);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void feedOnlyReachableAnimals(GameTestHelper h) throws ReflectiveOperationException {
        var a=CompatGameTests.platform(h,1);var b=CompatGameTests.platform(h,10);var m=CompatGameTests.maid(h,a);home(m);m.setTask(new TaskFeedAnimal());
        var own=EntityType.SHEEP.create(h.getLevel());var other=EntityType.SHEEP.create(h.getLevel());
        own.setPos(m.position().add(1,0,0));other.setPos(m.position().add(0,0,1));
        ((EntityMovementExtension)own).sable$setTrackingSubLevel(a);((EntityMovementExtension)other).sable$setTrackingSubLevel(b);
        h.getLevel().addFreshEntity(own);h.getLevel().addFreshEntity(other);
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.WHEAT,8));
        m.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,new NearestVisibleLivingEntities(m,List.of(other,own)));
        invoke(new MaidFeedAnimalTask(0.5f,10),"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),m,0L);
        h.assertTrue(own.isInLove() && !other.isInLove(),"Feed the reachable same-structure sheep, skipping a closer sheep on another structure");
        m.discard();own.discard();other.discard();CompatGameTests.cleanup(h,a,b);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void entitySearchAndCombat(GameTestHelper h) throws ReflectiveOperationException {
        var a=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,a);home(m);
        m.setTask(new TaskBowAttack());m.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.ARROW,16));
        var enemy=EntityType.ZOMBIE.create(h.getLevel());enemy.setPos(m.position().add(10,0,0));h.getLevel().addFreshEntity(enemy);
        h.assertTrue(m.searchDimension().contains(enemy.position()),"Local Home entity query must be projected to world space");
        invoke(new MaidNearestLivingEntitySensor(),"doTick",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m);
        h.assertTrue(m.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow().contains(enemy),"Ranged search must retain off-structure enemies");
        h.assertTrue(Combat.plan(m,enemy).allowed() && Combat.plan(m,enemy).destination()==null,"Bow should attack in range without leaving the deck");
        long before=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,m.getBoundingBox().inflate(30)).size();
        m.performRangedAttack(enemy,1);
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,m.getBoundingBox().inflate(30)).size()>before,"Actual ranged attack must create a projectile");
        m.setTask(new TaskAttack());m.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));Combat.clear(m);
        h.assertTrue(!Combat.plan(m,enemy).allowed(),"Melee must reject enemy beyond all reachable attack positions");
        m.discard();enemy.discard();CompatGameTests.cleanup(h,a);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void bedSearchSleepAndWake(GameTestHelper h) throws ReflectiveOperationException {
        var a=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,a);home(m);
        BlockPos head=bed(h,a.getPlot().getCenterBlock().offset(2,1,2));
        var task=new MaidBedTask(0.6f,3);
        h.assertTrue(head.equals(invoke(task,"findBed",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m)),"Bed POI must be found and reachable in local space");
        m.getBrain().setMemory(InitEntities.TARGET_POS.get(),new BlockPosTracker(head));
        invoke(task,"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),m,0L);
        h.assertTrue(m.isSleeping() && m.position().distanceTo(Spaces.world(a,head.getCenter()))<1,"Entering bed must not put entity into plot coordinates");
        a.logicalPose().position().add(30,0,20);a.logicalPose().orientation().rotateY(Math.PI/2);
        Resting.tick(m);invoke(m,"onMaidSleep",new Class[]{});
        h.assertTrue(m.position().distanceTo(Spaces.world(a,Vec3.atLowerCornerOf(head).add(0.5,0.5625,0.5)))<0.001,"Sleeping position must move with the bed");
        m.stopSleeping();
        h.assertTrue(!m.isSleeping() && !h.getLevel().getBlockState(head).getValue(BlockMaidBed.OCCUPIED),"Waking must release bed occupancy");
        h.assertTrue(m.position().distanceTo(Spaces.world(a,head.getCenter()))<6,"Wake position must remain near the world-space bed");
        m.discard();CompatGameTests.cleanup(h,a);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void keyboardSeatAndActivityExit(GameTestHelper h) throws ReflectiveOperationException {
        var a=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,a);home(m);
        BlockPos pos=a.getPlot().getCenterBlock().offset(4,1,3);
        var keyboard=InitBlocks.KEYBOARD.get().defaultBlockState();h.getLevel().setBlockAndUpdate(pos,keyboard);
        var task=new MaidJoyTask(0.6f,3);
        h.assertTrue(pos.equals(invoke(task,"findJoy",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m)),"Keyboard POI should be reachable");
        m.getBrain().setMemory(InitEntities.TARGET_POS.get(),new BlockPosTracker(pos));
        invoke(task,"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),m,0L);
        h.assertTrue(m.getVehicle() instanceof EntitySit,"Joy task must create and mount a seat");
        var seat=(EntitySit)m.getVehicle();Resting.seat(seat);Vec3 old=seat.position();
        a.logicalPose().position().add(40,0,10);a.logicalPose().orientation().rotateY(0.8);Resting.seat(seat);seat.positionRider(m);
        h.assertTrue(seat.position().distanceTo(old)<0.001 && m.position().distanceTo(Spaces.world(a,old))<2,"Retained seat stays local and Sable projects its passenger to world space");
        Navigation.state(m).activity=Activity.REST;Navigation.refresh(m);
        h.assertTrue(!m.isPassenger() && seat.isRemoved(),"Schedule change must dismount and release seat before commuting");
        m.discard();CompatGameTests.cleanup(h,a);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void accidentalDepartureAndRecovery(GameTestHelper h) {
        var a=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,a);var owner=CompatGameTests.player(h);
        m.tame(owner);CompatGameTests.OWNERS.put(m,owner);owner.setPos(m.position().add(0,100,0));
        Navigation.refresh(m);
        m.setPos(m.position().add(0,-10,0));((EntityMovementExtension)m).sable$setTrackingSubLevel(null);Navigation.refresh(m);
        Navigation.follow(m);h.assertTrue(Spaces.tracking(m)==null,"Short loss of tracking must not immediately teleport");
        Navigation.state(m).leftAt=m.level().getGameTime()-20;
        for(int i=0;i<30 && Spaces.tracking(m)==null;i++) { Navigation.state(m).retryAt=0;Navigation.follow(m); }
        h.assertTrue(Spaces.tracking(m)==a,"If flying owner has no landing, recover to the saved waiting structure");
        m.discard();CompatGameTests.cleanup(h,a);h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void compassActivityCommuting(GameTestHelper h) {
        var blocks=new ArrayList<BlockPos>();BlockPos origin=h.absolutePos(new BlockPos(1,3,1));
        for (int x=0;x<40;x++) for (int z=0;z<5;z++) {
            BlockPos p=origin.offset(x,0,z);h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());blocks.add(p);
        }
        var ship=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),origin,blocks,
                Objects.requireNonNull(dev.ryanhcode.sable.companion.math.BoundingBox3i.from(blocks)).expand(1,1,1));
        var selected=new java.util.concurrent.atomic.AtomicReference<>(Activity.WORK);
        var owner=CompatGameTests.player(h);
        var m=new EntityMaid(h.getLevel()) {
            @Override public Activity getScheduleDetail() { return selected.get(); }
            @Override public LivingEntity getOwner() { return owner; }
        };
        var base=ship.getPlot().getCenterBlock();
        m.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(base.offset(3,1,2))));
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(ship);m.setOnGround(true);m.tame(owner);h.getLevel().addFreshEntity(m);
        ItemStack compass=new ItemStack(InitItems.KAPPA_COMPASS.get());owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,compass);
        BlockPos[] areas={base.offset(3,0,2),base.offset(19,0,2),base.offset(35,0,2)};
        for (BlockPos p:areas) compass.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(owner,net.minecraft.world.InteractionHand.MAIN_HAND,
                new BlockHitResult(p.getCenter(),Direction.UP,p,false)));
        compass.getItem().interactLivingEntity(compass,owner,m,net.minecraft.world.InteractionHand.MAIN_HAND);
        m.setHomeModeEnable(true);Navigation.refresh(m);
        h.assertTrue(Homes.binding(m).points.size()==3,"Real compass recording must bind all three areas");
        Activity[] activities={Activity.WORK,Activity.IDLE,Activity.REST};
        for (int i=0;i<3;i++) {
            selected.set(activities[i]);Navigation.refresh(m);
            h.assertTrue(m.getRestrictCenter().equals(areas[i]) && Work.blockAllowed(m,areas[i]),"Schedule must select the corresponding compass area");
            h.assertTrue(!Work.blockAllowed(m,areas[(i+1)%3]),"Tasks must not use a different activity area");
            if (i>0) {
                Vec3 before=m.position();m.tickCount=40;Homes.tick(m);
                var walk=m.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
                h.assertTrue(walk.getTarget().currentBlockPosition().equals(areas[i]) && before.equals(m.position()),"Connected activity regions must commute by a local path");
                var route=m.getNavigation().createPath(areas[i],1);
                h.assertTrue(route!=null && route.canReach(),"Commute must have a complete route along this structure");
                for (int n=0;n<route.getNodeCount();n++) h.assertTrue(Spaces.belongs(h.getLevel(),route.getNode(n).asBlockPos(),ship),"Every commute node must remain in the selected structure");
            }
            m.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(areas[i].above())));
        }
        m.discard();CompatGameTests.cleanup(h,ship);h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void removedBedAndHomeRecovery(GameTestHelper h) {
        var a=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,a);home(m);
        var head=bed(h,a.getPlot().getCenterBlock().offset(2,1,2));m.startSleeping(head);
        h.getLevel().setBlockAndUpdate(head,Blocks.STONE.defaultBlockState());Resting.tick(m);
        h.assertTrue(!m.isSleeping(),"Replacing a bed with a solid block must end sleeping");
        m.setPos(m.position().add(0,-20,0));((EntityMovementExtension)m).sable$setTrackingSubLevel(null);
        Navigation.state(m).leftAt=m.level().getGameTime()-20;
        m.setInSittingPose(true);
        Homes.tick(m);h.assertTrue(Spaces.tracking(m)==null,"Home recovery must respect sitting control");
        m.setInSittingPose(false);
        for(int i=0;i<30 && Spaces.tracking(m)==null;i++) { Navigation.state(m).retryAt=0;Homes.tick(m); }
        h.assertTrue(Spaces.tracking(m)==a,"Home mode must recover to its bound structure after falling");
        m.discard();CompatGameTests.cleanup(h,a);h.succeed();
    }
}
