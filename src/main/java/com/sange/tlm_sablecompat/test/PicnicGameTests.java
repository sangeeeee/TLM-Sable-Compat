package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.block.BlockPicnicMat;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindHomeMealTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidHomeMealTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@PrefixGameTestTemplate(false)
public class PicnicGameTests {
    private static TileEntityPicnicMat place(ServerLevel level, BlockPos center) {
        var block=InitBlocks.PICNIC_MAT.get();
        level.setBlockAndUpdate(center,block.defaultBlockState());
        block.setPlacedBy(level,center,block.defaultBlockState(),null,new ItemStack(InitItems.PICNIC_BASKET.get()));
        var mat=(TileEntityPicnicMat)level.getBlockEntity(center);
        mat.getHandler().setStackInSlot(0,new ItemStack(Items.APPLE,8));
        return mat;
    }
    private static EntityMaid idle(GameTestHelper h, Vec3 position) {
        var maid=new EntityMaid(h.getLevel()) { @Override public Activity getScheduleDetail() { return Activity.IDLE; } };
        maid.setPos(position);maid.setOnGround(true);maid.setNoAi(true);h.getLevel().addFreshEntity(maid);return maid;
    }
    private static void eat(GameTestHelper h,EntityMaid maid,TileEntityPicnicMat mat) throws ReflectiveOperationException {
        var meal=new MaidHomeMealTask();int before=mat.getHandler().getStackInSlot(0).getCount();
        h.assertTrue((boolean)WorkGameTests.invoke(meal,"checkExtraStartConditions",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),maid),"Seated maid must resolve picnic food inventory");
        WorkGameTests.invoke(meal,"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),maid,0L);
        h.assertTrue(mat.getHandler().getStackInSlot(0).getCount()==before-1 && maid.isUsingItem(),"Native meal task must take one food and start eating");
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="picnic")
    public static void picnicSearchSeatsAndFoodOnMovingShip(GameTestHelper h) throws ReflectiveOperationException {
        var ship=CompatGameTests.platform(h,1);BlockPos center=ship.getPlot().getCenterBlock().offset(3,1,3);
        var mat=place(h.getLevel(),center);ship.logicalPose().position().add(0,20,0);ship.logicalPose().orientation().rotateY(.7);
        var maids=new ArrayList<EntityMaid>();var seats=new ArrayList<EntitySit>();
        for(int i=0;i<4;i++) {
            var maid=idle(h,Spaces.world(ship,Vec3.atBottomCenterOf(center.west()).add(0,.0625,0)));
            ((EntityMovementExtension)maid).sable$setTrackingSubLevel(ship);
            Homes.setSimple(maid);maid.setHomeModeEnable(true);maid.getSchedulePos().restrictTo(maid);
            var find=new MaidFindHomeMealTask(.6f,3);
            h.assertTrue(center.equals(WorkGameTests.invoke(find,"findPicnicMat",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),maid)),"Search must discover a picnic center in local coordinates");
            h.assertTrue((boolean)WorkGameTests.invoke(find,"checkExtraStartConditions",new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),maid),"Maid must recognize arrival at picnic mat");
            WorkGameTests.invoke(find,"start",new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),maid,0L);
            h.assertTrue(maid.getVehicle() instanceof EntitySit,"Picnic task must seat the maid");
            var seat=(EntitySit)maid.getVehicle();
            for(int tick=0;tick<25;tick++) { seat.tick();seat.positionRider(maid); }
            h.assertTrue(!seat.isRemoved() && maid.getVehicle()==seat,"Picnic seat must survive the compat block validation");
            eat(h,maid,mat);maids.add(maid);seats.add(seat);
        }
        h.assertTrue(Arrays.stream(mat.getSitIds()).distinct().count()==4,"Four distinct seats must stay occupied");
        ship.logicalPose().position().add(5,2,3);ship.logicalPose().orientation().rotateY(.4).rotateX(.03);
        for(int i=0;i<4;i++) {
            var seat=seats.get(i);var maid=maids.get(i);seat.tick();seat.positionRider(maid);
            h.assertTrue(maid.position().distanceTo(Spaces.world(ship,seat.position()))<2 && Spaces.tracking(maid)==ship,"Seated maid must move with rotated ship");
        }
        h.getLevel().setBlockAndUpdate(center,Blocks.STONE.defaultBlockState());
        for(var seat:seats) { seat.tick();h.assertTrue(seat.isRemoved(),"Replacing picnic center must still revoke its seats"); }
        maids.forEach(EntityMaid::discard);CompatGameTests.cleanup(h,ship);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="picnic")
    public static void assembledPicnicKeepsCenterAndFood(GameTestHelper h) {
        BlockPos center=h.absolutePos(new BlockPos(3,4,3));var blocks=new ArrayList<BlockPos>();
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
            BlockPos p=center.offset(x,-1,z);h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());blocks.add(p);blocks.add(p.above());
        }
        place(h.getLevel(),center);
        var ship=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),center,blocks,Objects.requireNonNull(BoundingBox3i.from(blocks)).expand(1,1,1));
        BlockPos local=ship.getPlot().getCenterBlock();
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
            var mat=(TileEntityPicnicMat)h.getLevel().getBlockEntity(local.offset(x,0,z));
            h.assertTrue(mat!=null && mat.getCenterPos().equals(local),"All assembled mat parts must address the moved center");
        }
        var centerMat=(TileEntityPicnicMat)h.getLevel().getBlockEntity(local);
        h.assertTrue(centerMat.getHandler().getStackInSlot(0).getCount()==8,"Assembly must preserve picnic food");
        CompatGameTests.cleanup(h,ship);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="picnic")
    public static void ordinaryPicnicStillWorks(GameTestHelper h) throws ReflectiveOperationException {
        BlockPos center=h.absolutePos(new BlockPos(3,4,3));
        for(BlockPos p:BlockPos.betweenClosed(center.offset(-2,-1,-2),center.offset(2,-1,2))) h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
        var mat=place(h.getLevel(),center);var maid=idle(h,Vec3.atBottomCenterOf(center));
        ((BlockPicnicMat)InitBlocks.PICNIC_MAT.get()).startMaidSit(maid,h.getLevel().getBlockState(center),h.getLevel(),center);
        var seat=(EntitySit)maid.getVehicle();
        for(int i=0;i<25;i++) { seat.tick();seat.positionRider(maid); }
        h.assertTrue(!seat.isRemoved() && !seat.getPersistentData().hasUUID(Resting.KEY),"Ordinary picnic keeps native seat behavior");
        eat(h,maid,mat);maid.discard();seat.discard();h.succeed();
    }
}
