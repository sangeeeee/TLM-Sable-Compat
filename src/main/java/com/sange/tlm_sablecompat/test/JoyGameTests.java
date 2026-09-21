package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidJoyTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.*;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
public class JoyGameTests {
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="joy")
    public static void playerUsesJoyBlocksOnStructure(GameTestHelper h) {
        var ship=CompatGameTests.platform(h,1);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(EntitySit.TYPE.is(dev.ryanhcode.sable.index.SableTags.RETAIN_IN_SUB_LEVEL),"Built-in datapack must retain TLM seats");
        h.assertTrue(EntitySit.TYPE.is(dev.ryanhcode.sable.index.SableTags.DESTROY_WITH_SUB_LEVEL),"Seats must be removed with their structure");
        BlockPos pos=ship.getPlot().getCenterBlock().offset(3,1,3);
        ship.logicalPose().position().add(8,0,3);ship.logicalPose().orientation().rotateY(.7);
        for(var block:List.of(InitBlocks.KEYBOARD.get(),InitBlocks.COMPUTER.get(),InitBlocks.BOOKSHELF.get())) {
            h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
            player.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(pos.west())));player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            ((BlockJoy)block).useItemOn(ItemStack.EMPTY,h.getLevel().getBlockState(pos),h.getLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
            h.assertTrue(player.getVehicle() instanceof EntitySit,"Empty-hand use must seat the player at "+block);
            var seat=(EntitySit)player.getVehicle();
            h.assertTrue(Sable.HELPER.getContaining(h.getLevel(),seat.position())==ship,"Seat must stay in the plot immediately after spawn");
            Vec3 local=seat.position();
            for(int i=0;i<30;i++) { seat.tick();seat.positionRider(player); }
            h.assertTrue(!seat.isRemoved() && player.getVehicle()==seat,"Player must stay seated over multiple ticks at "+block);
            ship.logicalPose().position().add(2,1,0);ship.logicalPose().orientation().rotateY(.3).rotateZ(.03);
            seat.tick();seat.positionRider(player);
            h.assertTrue(seat.position().distanceTo(local)<.001 && player.position().distanceTo(Spaces.world(ship,local))<2,"Player must follow translated and rotated retained seat");
            player.stopRiding();
            h.assertTrue(player.position().distanceTo(Spaces.world(ship,local))<4,"Dismount must remain in world space near the structure");
            seat.discard();h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        }
        CompatGameTests.cleanup(h,ship);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="joy")
    public static void maidCommutesToComputerAndStaysIdle(GameTestHelper h) throws ReflectiveOperationException {
        var ship=CompatGameTests.platform(h,1);var activity=new AtomicReference<>(Activity.IDLE);
        var m=new EntityMaid(h.getLevel()) { @Override public Activity getScheduleDetail() { return activity.get(); } };
        BlockPos base=ship.getPlot().getCenterBlock(),pos=base.offset(6,1,3);
        h.getLevel().setBlockAndUpdate(pos,InitBlocks.COMPUTER.get().defaultBlockState());
        // A roof makes the computer's own cell unusable as a walking endpoint.
        h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());
        m.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(base.offset(1,1,3))));m.setOnGround(true);
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(ship);h.getLevel().addFreshEntity(m);
        var binding=new Binding(true);binding.touchedStructure=true;
        binding.points.add(Spaces.point(h.getLevel(),ship,base.offset(1,0,3),base.offset(1,0,3)));
        binding.points.add(Spaces.point(h.getLevel(),ship,base.offset(5,0,3),base.offset(5,0,3)));
        binding.points.add(binding.points.getFirst());
        Homes.attach(m,BindingStore.get(h.getLevel()).add(binding));m.setHomeModeEnable(true);m.getSchedulePos().setConfigured(true);Navigation.refresh(m);
        var task=new MaidJoyTask(.6f,3);
        h.assertTrue(pos.equals(WorkGameTests.invoke(task,"findJoy",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m)),"Leisure area must discover a computer with a reachable adjacent seat approach");
        WorkGameTests.invoke(task,"checkExtraStartConditions",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m);
        var walk=m.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        BlockPos stand=walk.getTarget().currentBlockPosition();
        h.assertTrue(!stand.equals(pos) && Work.reachable(m,stand,0),"Joy task must walk beside the blocked computer cell");
        m.setPos(Spaces.world(ship,Vec3.atBottomCenterOf(stand)));m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        var arrived=new MaidJoyTask(.6f,3);
        h.assertTrue((boolean)WorkGameTests.invoke(arrived,"checkExtraStartConditions",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m),"Joy task recognizes arrival in local coordinates");
        WorkGameTests.invoke(arrived,"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),m,0L);
        h.assertTrue(m.getVehicle() instanceof EntitySit,"Maid must actually use the computer");var seat=(EntitySit)m.getVehicle();
        for(int i=0;i<40;i++) { seat.tick();seat.positionRider(m); }
        h.assertTrue(m.getVehicle()==seat && !seat.isRemoved(),"Idle maid must stay seated and run native joy ticks");
        Vec3 local=seat.position();ship.logicalPose().position().add(10,0,5);ship.logicalPose().orientation().rotateY(.8);
        seat.tick();seat.positionRider(m);
        h.assertTrue(m.position().distanceTo(Spaces.world(ship,local))<2,"Seated maid must follow the structure");
        h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(m.getYRot()-Resting.seatYaw(seat)))<.01,"Maid facing must use world-space seat orientation");
        activity.set(Activity.REST);Navigation.refresh(m);
        h.assertTrue(!m.isPassenger() && seat.isRemoved(),"Schedule transition must release the leisure seat before commuting");
        m.discard();CompatGameTests.cleanup(h,ship);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="joy")
    public static void joyRemovalAndOrdinaryWorld(GameTestHelper h) {
        var ship=CompatGameTests.platform(h,1);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        BlockPos pos=ship.getPlot().getCenterBlock().offset(3,1,3);var block=InitBlocks.KEYBOARD.get();
        h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        ((BlockJoy)block).useItemOn(ItemStack.EMPTY,block.defaultBlockState(),h.getLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        var seat=(EntitySit)player.getVehicle();seat.tick();seat.positionRider(player);
        h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.assertTrue(seat.isRemoved() && !player.isPassenger(),"Removing the joy block must release its seat");
        h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        ((BlockJoy)block).useItemOn(ItemStack.EMPTY,block.defaultBlockState(),h.getLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        seat=(EntitySit)player.getVehicle();seat.tick();seat.positionRider(player);CompatGameTests.cleanup(h,ship);
        h.assertTrue(seat.isRemoved() && !player.isPassenger(),"Removing the structure must not leave a ghost seat");
        pos=h.absolutePos(new BlockPos(3,4,3));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        ((BlockJoy)block).useItemOn(ItemStack.EMPTY,block.defaultBlockState(),h.getLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        seat=(EntitySit)player.getVehicle();for(int i=0;i<15;i++) { seat.tick();seat.positionRider(player); }
        h.assertTrue(!seat.isRemoved() && player.isPassenger() && !seat.getPersistentData().hasUUID(Resting.KEY),"Ordinary-world leisure seats retain native behavior");
        player.stopRiding();seat.discard();h.succeed();
    }
}

