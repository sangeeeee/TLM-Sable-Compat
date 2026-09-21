package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarrelBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.TapBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.block.plant.GrapeCropBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModItems;
import com.sange.tlm_sablecompat.*;
import com.winexp.maidtavern.entity.MaidTavernEntities;
import com.winexp.maidtavern.maid.brewing.*;
import com.winexp.maidtavern.maid.brewing.barrel.*;
import com.winexp.maidtavern.maid.brewing.bottle.*;
import com.winexp.maidtavern.maid.brewing.common.MaidBrewingPreTickTask;
import com.winexp.maidtavern.maid.brewing.storage.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@PrefixGameTestTemplate(false)
public class TavernGameTests {
    private static final ResourceLocation VODKA=ResourceLocation.parse("kaleidoscope_tavern:barrel/vodka");
    private static Object call(Object task,String method,GameTestHelper h,EntityMaid maid,boolean time) {
        try {
            return time ? WorkGameTests.invoke(task,method,new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),maid,0L)
                    : WorkGameTests.invoke(task,method,new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),maid);
        } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private static void home(EntityMaid maid) {
        Homes.setSimple(maid);maid.setHomeModeEnable(true);maid.getSchedulePos().restrictTo(maid);maid.setNoAi(true);
    }
    private static void pose(ServerSubLevel s,EntityMaid maid,Vec3 local) {
        var pose=s.logicalPose();
        dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer.getContainer(s.getLevel()).physicsSystem().getPipeline().teleport(s,new org.joml.Vector3d(pose.position()).add(35,30,21),
                new org.joml.Quaterniond(pose.orientation()).rotateY(.7).rotateX(.06));
        maid.setPos(Spaces.world(s,local).add(0,.04,0));maid.setOnGround(true);
    }
    private static void arrive(EntityMaid maid,ServerSubLevel s) {
        var walk=maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        var path=maid.getNavigation().createPath(walk.getTarget().currentBlockPosition(),0);
        BlockPos feet=path!=null && path.canReach() ? path.getEndNode().asBlockPos() : walk.getTarget().currentBlockPosition();
        maid.setPos(Spaces.world(s,Vec3.atBottomCenterOf(feet)).add(0,.04,0));
        maid.setOnGround(true);maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);maid.getNavigation().stop();
    }
    private static BarrelBlockEntity barrel(GameTestHelper h,BlockPos pos,EntityMaid maid) {
        var state=ModBlocks.BARREL.get().defaultBlockState();
        h.getLevel().setBlock(pos,state,2);
        ModBlocks.BARREL.get().setPlacedBy(h.getLevel(),pos,state,maid,ModItems.BARREL.toStack());
        return (BarrelBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void list(EntityMaid maid,List<BlockPos> barrels) {
        maid.getBrain().setMemory(MaidTavernEntities.BREWING_LIST.get(),new BrewingList.Builder()
                .put(VODKA,new BrewingList.Config(1,barrels)).build());
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void grapesOnRotatedStructure(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1);var maid=CompatGameTests.maid(h,s);home(maid);
        var task=TaskManager.findTask(ResourceLocation.parse("maidtavern:grape")).orElseThrow();maid.setTask(task);
        BlockPos feet=s.getPlot().getCenterBlock().offset(3,1,3), grape=feet.east().above();
        h.getLevel().setBlock(grape,ModBlocks.GRAPE_CROP.get().defaultBlockState().setValue(GrapeCropBlock.AGE,5),2);
        maid.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));pose(s,maid,Vec3.atBottomCenterOf(feet));
        var tasks=task.createBrainTasks(maid);
        call(tasks.getFirst().getSecond(),"start",h,maid,true);
        h.assertTrue(maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get()),"Grape search must find a local reachable base");
        arrive(maid,s);
        h.assertTrue((boolean)call(tasks.get(1).getSecond(),"checkExtraStartConditions",h,maid,false),"Rotated grape task must pass arrival");
        call(tasks.get(1).getSecond(),"start",h,maid,true);
        h.assertTrue(h.getLevel().getBlockState(grape).isAir(),"Actual shears task must harvest mature grapes");
        h.assertTrue(maid.getMainHandItem().getDamageValue()==1,"Harvest must consume one shears durability");
        maid.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void brewingStorageAndInterruptedIngredients(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1);var maid=CompatGameTests.maid(h,s);
        BlockPos origin=s.getPlot().getCenterBlock(), base=origin.offset(2,1,3), chestPos=origin.offset(6,1,5);
        var barrel=barrel(h,base,maid);
        barrel.setOpen(false);
        // Normal barrel interaction is at the lid. Provide stairs to an adjacent work platform.
        for(BlockPos p:List.of(origin.offset(5,1,3),origin.offset(4,1,3),origin.offset(4,2,3)))
            h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        maid.setPos(Spaces.world(s,Vec3.atBottomCenterOf(origin.offset(6,1,3))));home(maid);
        var task=(TaskBrewing)TaskManager.findTask(ResourceLocation.parse("maidtavern:brewing")).orElseThrow();maid.setTask(task);list(maid,List.of(base));
        h.getLevel().setBlock(chestPos,Blocks.CHEST.defaultBlockState(),2);
        var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(chestPos);
        chest.setItem(0,new ItemStack(Items.POTATO,16));
        for(int i=1;i<=4;i++) chest.setItem(i,new ItemStack(Items.WATER_BUCKET));
        chest.setItem(5,ModItems.EMPTY_BOTTLE.toStack(4));
        maid.getBrain().setMemory(MaidTavernEntities.STORAGE_BINDING.get(),new StorageBinding(List.of(chestPos),List.of(chestPos),List.of(chestPos)));
        pose(s,maid,AddonWork.position(maid));
        var storageMove=new MaidBrewingMoveToStorageTask(task,.45f,4,3,20);
        call(storageMove,"start",h,maid,true);
        h.assertTrue(MaidBrewingStateManager.isWorking(maid),"Storage search must select actual bound chest and barrel");
        if(!MaidBrewingStateManager.getWork(maid).isCloseEnough(maid)) arrive(maid,s);
        var storage=new MaidBrewingStorageOperationTask(task);
        h.assertTrue((boolean)call(storage,"checkExtraStartConditions",h,maid,false),"Local chest must be interactable");
        call(storage,"start",h,maid,true);
        // Upstream prioritizes empty bottles; ingredient collection may be a second visit.
        if(!task.hasIngredients(maid,VODKA)) {
            call(storageMove,"start",h,maid,true);
            if(MaidBrewingStateManager.getWork(maid)!=null && !MaidBrewingStateManager.getWork(maid).isCloseEnough(maid)) arrive(maid,s);
            call(storage,"start",h,maid,true);
        }
        h.assertTrue(chest.getItem(0).isEmpty() && task.hasIngredients(maid,VODKA),"Take actual recipe ingredients from storage");
        var move=new MaidBrewingMoveToBarrelTask(task,.45f,4,2.5,20);
        call(move,"start",h,maid,true);
        var work=MaidBrewingStateManager.getWork(maid);
        h.assertTrue(work!=null,"Bound barrel must create work");
        var standing=maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().getTarget().currentBlockPosition();
        h.assertTrue(!standing.equals(base.above(2)) && Work.reachable(maid,standing,0),"Must navigate to reachable space beside the lid");
        var add=new MaidBrewingAddIngredientsTask(task,1);
        maid.setPos(Spaces.world(s,Vec3.atBottomCenterOf(origin.offset(6,1,6))));
        h.assertTrue(!work.isCloseEnough(maid),"Pre-arrival fixture must be out of reach");
        call(add,"tick",h,maid,true);
        h.assertTrue(barrel.getFluid().isEmpty(),"Must not add ingredients before arriving");
        arrive(maid,s);
        h.assertTrue((boolean)call(add,"checkExtraStartConditions",h,maid,false),"Lid arrival must accept its own multiblock parts");
        call(add,"tick",h,maid,true);call(add,"tick",h,maid,true);
        h.assertTrue(barrel.getFluid().getFluidAmount()==4000,"Exactly four buckets must be added");
        maid.setPos(Spaces.world(s,Vec3.atBottomCenterOf(origin.offset(6,1,3))));
        h.assertTrue(!(boolean)call(add,"canStillUse",h,maid,true),"Moving away must stop continued barrel operations");
        call(add,"stop",h,maid,true);
        h.assertTrue(maid.getBrain().getMemory(MaidTavernEntities.BREWING_SESSION.get()).orElseThrow().stage()==BrewingSession.Stage.FLUIDS_PLACED,"Interrupted fluid stage must survive: "+maid.getBrain().getMemory(MaidTavernEntities.BREWING_SESSION.get()));
        call(move,"start",h,maid,true);arrive(maid,s);
        call(add,"tick",h,maid,true);call(add,"tick",h,maid,true);
        h.assertTrue(!barrel.isOpen() && barrel.getIngredient().getStackInSlot(0).getCount()==16,"Resume ingredients and close lid without duplicating water");
        h.assertTrue(maid.getBrain().getMemory(MaidTavernEntities.BREWING_SESSION.get()).isEmpty(),"Finished session must clear normally");
        maid.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void bottlesAndResultsOnStructure(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1);var maid=CompatGameTests.maid(h,s);home(maid);
        var task=new TaskBrewing();maid.setTask(task);list(maid,List.of());
        BlockPos base=s.getPlot().getCenterBlock().offset(2,1,3), bottlePos=base.east(2);
        var barrel=barrel(h,base,maid);
        // Mature the real barrel using its normal saved state; fermentation duration is not a coordinate test.
        var data=barrel.saveWithoutMetadata(h.getLevel().registryAccess());
        data.putInt("brew_level",1);data.putString("recipe_id",VODKA.toString());
        barrel.loadWithComponents(data,h.getLevel().registryAccess());
        barrel.getOutput().setStackInSlot(0,ModItems.VODKA.toStack(8));
        h.getLevel().setBlock(bottlePos.above(),ModBlocks.TAP.get().defaultBlockState().setValue(TapBlock.FACING,Direction.EAST),2);
        maid.getMaidInv().setStackInSlot(0,ModItems.EMPTY_BOTTLE.toStack(2));
        pose(s,maid,Vec3.atBottomCenterOf(bottlePos.south()));
        Vec3 originalFeet=maid.position();
        h.assertTrue(AddonWork.position(maid).distanceTo(Vec3.atBottomCenterOf(bottlePos.south()))<.1,"Initial bottle fixture pose");
        var move=new MaidBrewingMoveToBottleTask(task,.45f,4,2,20);call(move,"start",h,maid,true);
        h.assertTrue(maid.position().distanceTo(originalFeet)<.001,"Search must not move maid: "+maid.position()+" != "+originalFeet);
        h.assertTrue(MaidBrewingStateManager.isWorking(maid),"Must find tap's bottle location in structure");
        var place=new MaidBrewingPlaceBottleTask(task);
        h.assertTrue((boolean)call(place,"checkExtraStartConditions",h,maid,false),"Bottle arrival must use local coordinates");
        call(place,"start",h,maid,true);
        h.assertTrue(maid.position().distanceTo(originalFeet)<.001,"Placing bottle must not move maid: "+maid.position()+" != "+originalFeet);
        h.assertTrue(h.getLevel().getBlockState(bottlePos).is(ModBlocks.EMPTY_BOTTLE.get()),"Actual task must place empty bottle");
        h.assertTrue(maid.getMaidInv().getStackInSlot(0).getCount()==1,"Only one empty bottle is used");
        // Use the barrel's real fill operation (the tap normally calls this after its animation).
        h.assertTrue(barrel.canTapExtract(h.getLevel(),bottlePos.above(),maid),"Real barrel must accept the placed bottle");
        barrel.doTapExtract(h.getLevel(),bottlePos.above());
        h.assertTrue(maid.position().distanceTo(originalFeet)<.001,"Filling bottle must not move maid: "+maid.position()+" != "+originalFeet);
        var take=new MaidBrewingTakeBottleTask(task);
        h.assertTrue((boolean)call(take,"checkExtraStartConditions",h,maid,false),"Recognize finished drink: valid="+task.isBottleValid(maid,bottlePos)+", allowed="+Work.blockAllowed(maid,bottlePos)+", visible="+TavernWork.visible(maid,bottlePos,AddonWork.position(maid))+", feet="+AddonWork.position(maid)+", target="+bottlePos);
        call(take,"start",h,maid,true);
        h.assertTrue(h.getLevel().getBlockState(bottlePos).isAir() && !task.getResultsToInsert(maid).isEmpty(),"Actual task must collect finished drink");
        TavernWork.clearWork(maid);
        BlockPos chestPos=bottlePos.south(2);h.getLevel().setBlock(chestPos,Blocks.CHEST.defaultBlockState(),2);
        maid.getBrain().setMemory(MaidTavernEntities.STORAGE_BINDING.get(),new StorageBinding(List.of(),List.of(chestPos),List.of()));
        call(new MaidBrewingMoveToStorageTask(task,.45f,4,3,20),"start",h,maid,true);
        call(new MaidBrewingStorageOperationTask(task),"start",h,maid,true);
        h.assertTrue(((ChestBlockEntity)h.getLevel().getBlockEntity(chestPos)).getItem(0).is(ModItems.VODKA.get()),"Store actual completed vodka in the bound result chest");
        maid.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void rejectForeignAndObstructedWork(GameTestHelper h) {
        var a=CompatGameTests.platform(h,1);var b=CompatGameTests.platform(h,12);var maid=CompatGameTests.maid(h,a);home(maid);
        var task=new TaskBrewing();maid.setTask(task);list(maid,List.of());
        BlockPos local=a.getPlot().getCenterBlock().offset(4,1,3), foreign=b.getPlot().getCenterBlock().offset(3,1,3);
        h.getLevel().setBlock(local,Blocks.CHEST.defaultBlockState(),2);h.getLevel().setBlock(foreign,Blocks.CHEST.defaultBlockState(),2);
        var work=new BrewingWork(BrewingWorkTypes.STORAGE,local,.45f,3);
        MaidBrewingStateManager.startWork(maid,work);
        h.assertTrue(work.isCloseEnough(maid),"Adjacent own-structure storage must be usable");
        // Within native range but a slab separates the maid from the chest.
        for(BlockPos p:BlockPos.betweenClosed(local.offset(-2,1,-2),local.offset(2,1,2))) h.getLevel().setBlock(p,Blocks.STONE_SLAB.defaultBlockState(),2);
        maid.setPos(Spaces.world(a,Vec3.atBottomCenterOf(local.west().above()).add(0,.5,0)));
        h.assertTrue(!work.isCloseEnough(maid),"Floor must block storage even within interaction radius");
        var foreignWork=new BrewingWork(BrewingWorkTypes.STORAGE,foreign,.45f,3);
        h.assertTrue(!foreignWork.isCloseEnough(maid),"Foreign structure storage must never be interactable");
        TavernWork.clearWork(maid);MaidBrewingStateManager.startWork(maid,foreignWork);
        h.assertTrue(!MaidBrewingStateManager.isWorking(maid),"Cached foreign target must be rejected before work starts");
        var foreignBarrel=barrel(h,foreign,maid);
        h.assertTrue(!task.isBarrelValid(maid,foreignBarrel),"Explicitly bound barrel on another structure must not bypass work scope");
        MaidBrewingStateManager.startWork(maid,work);
        maid.setHomeModeEnable(false);
        var owner=CompatGameTests.player(h);CompatGameTests.OWNERS.put(maid,owner);
        ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)owner).sable$setTrackingSubLevel(b);
        call(new MaidBrewingPreTickTask(),"start",h,maid,true);
        h.assertTrue(!MaidBrewingStateManager.isWorking(maid),"Changing owner's structure must invalidate stale work");
        maid.discard();CompatGameTests.cleanup(h,a,b);h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void compassScopeAndInvalidation(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1);
        var activity=new java.util.concurrent.atomic.AtomicReference<>(net.minecraft.world.entity.schedule.Activity.WORK);
        var maid=new EntityMaid(h.getLevel()) {
            @Override public net.minecraft.world.entity.schedule.Activity getScheduleDetail() { return activity.get(); }
        };
        BlockPos origin=s.getPlot().getCenterBlock(), chest=origin.offset(1,1,1);
        maid.setPos(Spaces.world(s,Vec3.atBottomCenterOf(chest.east())));
        ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)maid).sable$setTrackingSubLevel(s);
        maid.setOnGround(true);maid.setNoAi(true);h.getLevel().addFreshEntity(maid);
        var binding=new Binding(true);binding.touchedStructure=true;
        for(BlockPos point:List.of(chest.below(),origin.offset(6,0,6),origin.offset(5,0,5)))
            binding.points.add(Spaces.point(h.getLevel(),s,point,point));
        Homes.attach(maid,BindingStore.get(h.getLevel()).add(binding));maid.setHomeModeEnable(true);maid.restrictTo(chest.below(),3);
        var task=new TaskBrewing();maid.setTask(task);list(maid,List.of());
        h.getLevel().setBlock(chest,Blocks.CHEST.defaultBlockState(),2);
        var distantBarrel=barrel(h,origin.offset(5,1,3),maid);
        h.assertTrue(!task.isBarrelValid(maid,distantBarrel),"Explicit barrel bindings must obey compass radius on the same structure");
        var work=new BrewingWork(BrewingWorkTypes.STORAGE,chest,.45f,3);
        MaidBrewingStateManager.startWork(maid,work);
        h.assertTrue(MaidBrewingStateManager.isWorking(maid),"Chest inside compass WORK area must be allowed");
        activity.set(net.minecraft.world.entity.schedule.Activity.IDLE);
        call(new MaidBrewingPreTickTask(),"start",h,maid,true);
        h.assertTrue(!MaidBrewingStateManager.isWorking(maid),"Changing to a different compass area must cancel old brewing work");
        activity.set(net.minecraft.world.entity.schedule.Activity.WORK);MaidBrewingStateManager.startWork(maid,work);
        binding.failure="split";call(new MaidBrewingPreTickTask(),"start",h,maid,true);
        h.assertTrue(!MaidBrewingStateManager.isWorking(maid) && !work.isCloseEnough(maid),"Invalidated compass must stop cached work immediately");
        maid.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void ordinaryWorldStorage(GameTestHelper h) {
        var maid=new EntityMaid(h.getLevel());BlockPos feet=h.absolutePos(new BlockPos(3,3,3)), chest=feet.east();
        for(BlockPos p:BlockPos.betweenClosed(feet.offset(-2,-1,-2),feet.offset(2,-1,2))) h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        maid.setPos(Vec3.atBottomCenterOf(feet));maid.setNoAi(true);h.getLevel().addFreshEntity(maid);
        var task=new TaskBrewing();maid.setTask(task);list(maid,List.of());
        h.getLevel().setBlock(chest,Blocks.CHEST.defaultBlockState(),2);
        var storage=(ChestBlockEntity)h.getLevel().getBlockEntity(chest);storage.setItem(0,ModItems.EMPTY_BOTTLE.toStack(4));
        var work=new BrewingWork(BrewingWorkTypes.STORAGE,chest,.45f,3);MaidBrewingStateManager.startWork(maid,work);
        var operation=new MaidBrewingStorageOperationTask(task);
        h.assertTrue((boolean)call(operation,"checkExtraStartConditions",h,maid,false),"Ordinary world must retain upstream storage conditions");
        call(operation,"start",h,maid,true);
        h.assertTrue(storage.getItem(0).isEmpty() && com.winexp.maidtavern.util.ItemHandlerUtil.countItems(maid.getAvailableInv(true),stack->stack.is(ModItems.EMPTY_BOTTLE.get()))==4,"Ordinary world transfer remains functional");
        maid.discard();h.succeed();
    }
}
