package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCookMoveTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Real optional task classes, recipes and block entities, in an isolated server world. */
@PrefixGameTestTemplate(false)
public class AddonGameTests {
    static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    static void home(EntityMaid m) { Homes.setSimple(m);m.setHomeModeEnable(true);m.getSchedulePos().restrictTo(m); }
    static void put(GameTestHelper h,BlockPos p,String block) { h.getLevel().setBlock(p,BuiltInRegistries.BLOCK.get(id(block)).defaultBlockState(),2); }
    static Object call(Object b,String name,GameTestHelper h,EntityMaid m,boolean time) throws ReflectiveOperationException {
        return time ? WorkGameTests.invoke(b,name,new Class[]{ServerLevel.class,EntityMaid.class,long.class},h.getLevel(),m,0L)
                : WorkGameTests.invoke(b,name,new Class[]{ServerLevel.class,EntityMaid.class},h.getLevel(),m);
    }
    static void target(EntityMaid m,BlockPos p) { m.getBrain().setMemory(InitEntities.TARGET_POS.get(),new BlockPosTracker(p)); }

    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void registeredTasks(GameTestHelper h) {
        var ids=TaskManager.getTaskMap().keySet().stream().filter(r->Set.of("maidsoulkitchen","kaleidoscope_compat","eclipticseasons_multimodpatch").contains(r.getNamespace())).sorted().toList();
        TLMSableCompat.LOGGER.info("Addon task inventory: {}",ids);
        for (String path:List.of("compat_melon","berries_farm","fruit_farm","feed_animal_t","eclipticseasons_farm","furnace","fd_cooking_pot","fd_cutting_board","fd_skillet","bd_basin","bd_grill"))
            h.assertTrue(ids.contains(id("maidsoulkitchen:"+path)),"Missing registered Kitchen task: "+path);
        for (String path:List.of("chopping_board","millstone","pressing_tub"))
            h.assertTrue(ids.contains(id("kaleidoscope_compat:"+path)),"Missing registered Kaleidoscope task: "+path);
        h.assertTrue(ids.contains(id("eclipticseasons_multimodpatch:clean_snow")),"Missing seasonal snow task");
        h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void choppingBoardOnMovingStructure(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(3,1,3);put(h,p,"kaleidoscope_cookery:chopping_board");
        var task=TaskManager.findTask(id("kaleidoscope_compat:chopping_board")).orElseThrow();m.setTask(task);
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.COD,2));
        m.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(id("kaleidoscope_cookery:iron_kitchen_knife"))));
        var behavior=task.createBrainTasks(m).getFirst().getSecond();
        s.logicalPose().position().add(60,0,40);s.logicalPose().orientation().rotateY(Math.PI/2);
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(p.north())));
        h.assertTrue(p.equals(call(behavior,"findChoppingBoard",h,m,false)),"Search must use moved local Home");
        h.assertTrue((boolean)call(behavior,"checkExtraStartConditions",h,m,false),"Board behavior must start");
        var standField=behavior.getClass().getDeclaredField("standPos");standField.setAccessible(true);
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf((BlockPos)standField.get(behavior))));
        call(behavior,"start",h,m,true);call(behavior,"tick",h,m,true);
        var board=(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity)h.getLevel().getBlockEntity(p);
        h.assertTrue(!board.getCurrentCutStack().isEmpty(),"Actual task must insert fish into the board");
        call(behavior,"tick",h,m,true);
        h.assertTrue(board.getCurrentCutCount()>0,"Actual task must cut on the moved structure");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void pressingTubMotionAndArrival(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(3,1,3);put(h,p,"kaleidoscope_tavern:pressing_tub");
        var task=TaskManager.findTask(id("kaleidoscope_compat:pressing_tub")).orElseThrow();m.setTask(task);
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.GLOW_BERRIES,4));
        var b=task.createBrainTasks(m).getFirst().getSecond();
        s.logicalPose().position().add(50,0,30);s.logicalPose().orientation().rotateY(Math.PI/2);
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(p.offset(-2,0,0))));
        h.assertTrue((boolean)call(b,"checkExtraStartConditions",h,m,false),"Tub search starts");call(b,"start",h,m,true);
        var tub=(com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IPressingTub)h.getLevel().getBlockEntity(p);
        call(b,"tick",h,m,true);
        h.assertTrue(tub.getItems().getStackInSlot(0).isEmpty(),"Must finish walking before inserting ingredients");
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(p).add(.2,0,0)));call(b,"tick",h,m,true);
        h.assertTrue(!tub.getItems().getStackInSlot(0).isEmpty(),"Task must insert berries after arrival");
        call(b,"tick",h,m,true);
        Vec3 toward=Spaces.world(s,Vec3.atBottomCenterOf(p)).subtract(m.position());
        h.assertTrue(m.getDeltaMovement().horizontalDistance()<.1 && m.getDeltaMovement().dot(new Vec3(toward.x,0,toward.z))>0,"Centering motion must rotate with the ship and remain bounded");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void millstoneNeverMovesMaidIntoPlot(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(3,1,3);put(h,p,"kaleidoscope_cookery:millstone");
        var task=TaskManager.findTask(id("kaleidoscope_compat:millstone")).orElseThrow();m.setTask(task);
        var b=task.createBrainTasks(m).getFirst().getSecond();
        h.assertTrue(p.equals(call(b,"findMillstone",h,m,false)),"Millstone must be reachable on this structure");
        var mill=(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.MillstoneBlockEntity)h.getLevel().getBlockEntity(p);
        mill.bindEntity(m);mill.onPutItem(h.getLevel(),new ItemStack(Items.PINK_TULIP));
        Vec3 old=AddonWork.position(m);s.logicalPose().position().add(40,0,20);m.setPos(Spaces.world(s,old));
        mill.tick(h.getLevel());
        h.assertTrue(mill.hasEntity() && m.position().distanceTo(Spaces.world(s,p.getCenter()))<5,"Millstone binding and forced movement must stay in world coordinates");
        h.assertTrue(mill.getProgressPercent()>0,"Bound millstone must advance its real recipe");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void kitchenSearchArrivalAndBoundaries(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var other=CompatGameTests.platform(h,10);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(4,1,3), foreign=other.getPlot().getCenterBlock().offset(3,1,3);
        String[][] cases={{"fd_cooking_pot","farmersdelight:cooking_pot"},{"fd_cutting_board","farmersdelight:cutting_board"},{"fd_skillet","farmersdelight:skillet"},{"bd_basin","barbequesdelight:basin"},{"bd_grill","barbequesdelight:grill"},{"furnace","minecraft:furnace"}};
        for (var row:cases) {
            var task=(ICookTask)TaskManager.findTask(id("maidsoulkitchen:"+row[0])).orElseThrow();m.setTask(task);put(h,p,row[1]);put(h,foreign,row[1]);
            var brains=task.createBrainTasks(m);var search=((com.mojang.datafixers.util.Pair<?,?>)brains.getFirst()).getSecond();var action=((com.mojang.datafixers.util.Pair<?,?>)brains.get(1)).getSecond();
            target(m,p);
            h.assertTrue((boolean)call(action,"checkExtraStartConditions",h,m,false),row[0]+" must recognize arrival at local work block");
            target(m,foreign);
            h.assertTrue(!(boolean)call(action,"checkExtraStartConditions",h,m,false),row[0]+" must reject foreign work block");
            h.assertTrue(!(boolean)WorkGameTests.invoke(search,"shouldMoveTo",new Class[]{ServerLevel.class,EntityMaid.class,BlockPos.class},h.getLevel(),m,foreign),row[0]+" search must reject foreign blocks before touching inventory");
            BlockPos isolated=s.getPlot().getCenterBlock().offset(12,1,3);put(h,isolated.below(),"minecraft:stone");put(h,isolated,row[1]);
            h.assertTrue(!(boolean)WorkGameTests.invoke(search,"shouldMoveTo",new Class[]{ServerLevel.class,EntityMaid.class,BlockPos.class},h.getLevel(),m,isolated),row[0]+" must reject unreachable stations");
        }
        m.discard();CompatGameTests.cleanup(h,s,other);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void kitchenCutActualRecipe(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(4,1,3);put(h,p,"farmersdelight:cutting_board");
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.ACACIA_LOG,2));m.getMaidInv().setStackInSlot(1,new ItemStack(Items.IRON_AXE));
        var task=TaskManager.findTask(id("maidsoulkitchen:fd_cutting_board")).orElseThrow();m.setTask(task);
        var tasks=task.createBrainTasks(m);var search=tasks.getFirst().getSecond();var action=tasks.get(1).getSecond();
        call(search,"searchForDestination",h,m,false);
        h.assertTrue(m.getBrain().getMemory(InitEntities.TARGET_POS.get()).orElseThrow().currentBlockPosition().equals(p),"Kitchen search must choose real cutting recipe station");
        call(action,"start",h,m,true);call(action,"tick",h,m,true);
        var board=(vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity)h.getLevel().getBlockEntity(p);
        h.assertTrue(board.getStoredItem().is(Items.ACACIA_LOG),"Actual Kitchen task must insert ingredient");
        for(int i=0;i<5;i++) call(action,"tick",h,m,true);
        h.assertTrue(board.getStoredItem().isEmpty(),"Actual Kitchen task must process the cutting recipe");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void cookingStationsOperateAndStopOutsideArea(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var other=CompatGameTests.platform(h,10);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(4,1,3);
        String[][] cases={{"fd_cooking_pot","farmersdelight:cooking_pot"},{"fd_skillet","farmersdelight:skillet"},{"bd_grill","barbequesdelight:grill"},{"bd_basin","barbequesdelight:basin"}};
        for(var row:cases) {
            m.getBrain().eraseMemory(InitEntities.TARGET_POS.get());m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            for(int i=0;i<m.getMaidInv().getSlots();i++) m.getMaidInv().setStackInSlot(i,ItemStack.EMPTY);
            m.getMaidInv().setStackInSlot(0,new ItemStack(Items.APPLE,8));
            m.getMaidInv().setStackInSlot(1,new ItemStack(Items.SUGAR,8));
            m.getMaidInv().setStackInSlot(2,new ItemStack(Items.BEEF,8));
            m.getMaidInv().setStackInSlot(3,new ItemStack(Items.STICK,8));
            m.getMaidInv().setStackInSlot(5,new ItemStack(BuiltInRegistries.ITEM.get(id("farmersdelight:cabbage")),8));
            if(row[0].equals("bd_grill")) m.getMaidInv().setStackInSlot(0,new ItemStack(BuiltInRegistries.ITEM.get(id("barbequesdelight:raw_beef_skewer")),8));
            put(h,p.below(),"minecraft:campfire");put(h,p,row[1]);
            var task=TaskManager.findTask(id("maidsoulkitchen:"+row[0])).orElseThrow();m.setTask(task);
            var tasks=task.createBrainTasks(m);var search=tasks.getFirst().getSecond();var action=tasks.get(1).getSecond();
            call(search,"searchForDestination",h,m,false);
            var manager=((MaidCookMoveTask<?,?>)search).getMaidRecipesManager();
            h.assertTrue(m.getBrain().getMemory(InitEntities.TARGET_POS.get()).isPresent(),row[0]+" must find a usable heated station with real ingredients; block="+h.getLevel().getBlockState(p)+", recipes="+manager.getRecipesIngredients().size()+", heated="+(h.getLevel().getBlockEntity(p) instanceof com.mao.barbequesdelight.content.block.GrillBlockEntity grill && grill.isHeated())+", inv="+(manager.getCookInv()==null?"null":manager.getCookInv().getInventoryItem()));
            call(action,"start",h,m,true);
            if(!row[0].equals("fd_cooking_pot")) call(action,"tick",h,m,true);
            var be=h.getLevel().getBlockEntity(p);
            boolean changed=switch(row[0]) {
                case "fd_cooking_pot" -> !((vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity)be).getInventory().getStackInSlot(0).isEmpty();
                case "fd_skillet" -> ((vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity)be).hasStoredStack();
                case "bd_grill" -> Arrays.stream(((com.mao.barbequesdelight.content.block.GrillBlockEntity)be).entries).anyMatch(e->!e.stack.isEmpty());
                default -> !((com.mao.barbequesdelight.content.block.BasinBlockEntity)be).items.isEmpty();
            };
            h.assertTrue(changed,row[0]+" actual task must insert ingredients");
            if(!row[0].equals("fd_cooking_pot")) {
                target(m,other.getPlot().getCenterBlock().offset(3,1,3));
                h.assertTrue(!(boolean)call(action,"canStillUse",h,m,true),row[0]+" must stop after target leaves selected structure");
            }
        }
        m.discard();CompatGameTests.cleanup(h,s,other);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void kitchenApproachAndContainerFrame(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var other=CompatGameTests.platform(h,10);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(6,1,3);
        put(h,p,"farmersdelight:cooking_pot");put(h,p.below(),"minecraft:campfire");
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(s.getPlot().getCenterBlock().offset(0,1,3))));
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.APPLE,8));m.getMaidInv().setStackInSlot(1,new ItemStack(Items.SUGAR,8));
        var task=TaskManager.findTask(id("maidsoulkitchen:fd_cooking_pot")).orElseThrow();m.setTask(task);
        var tasks=task.createBrainTasks(m);var search=tasks.getFirst().getSecond();var action=tasks.get(1).getSecond();
        call(search,"searchForDestination",h,m,false);
        var walk=m.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        BlockPos stand=walk.getTarget().currentBlockPosition();
        h.assertTrue(!stand.equals(p) && Work.reachable(m,stand,0),"Cooking must walk to a reachable standing point beside the pot");
        h.assertTrue(!(boolean)call(action,"checkExtraStartConditions",h,m,false) && m.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get()),"Work address must survive walking to a separate standing point");
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(stand)));
        h.assertTrue((boolean)call(action,"checkExtraStartConditions",h,m,false),"Cooking starts after reaching standing point");
        var manager=((MaidCookMoveTask<?,?>)search).getMaidRecipesManager();
        h.assertTrue(!(boolean)WorkGameTests.invoke(manager,"isPosZone",new Class[]{BlockPos.class},p),"Container on same moved structure remains in range");
        BlockPos foreign=other.getPlot().getCenterBlock().offset(3,1,3);
        h.assertTrue((boolean)WorkGameTests.invoke(manager,"isPosZone",new Class[]{BlockPos.class},foreign),"Container on another structure must be excluded");
        var owner=CompatGameTests.player(h);m.tame(owner);CompatGameTests.OWNERS.put(m,owner);
        ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)owner).sable$setTrackingSubLevel(other);
        owner.setPos(Spaces.world(other,foreign.getCenter()));m.setHomeModeEnable(false);
        m.getBrain().eraseMemory(InitEntities.TARGET_POS.get());m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        call(search,"searchForDestination",h,m,false);
        h.assertTrue(!m.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get()),"Follow maid must transfer to owner's B before accepting work on B or old A");
        m.discard();CompatGameTests.cleanup(h,s,other);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void kitchenBerryAndAnimalWork(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var other=CompatGameTests.platform(h,10);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(6,1,3);
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(s.getPlot().getCenterBlock().offset(0,1,3))));
        h.getLevel().setBlock(p,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE,3),2);
        var berries=TaskManager.findTask(id("maidsoulkitchen:berries_farm")).orElseThrow();m.setTask(berries);
        var tasks=berries.createBrainTasks(m);call(tasks.getFirst().getSecond(),"searchForDestination",h,m,false);
        h.assertTrue(m.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get()),"Kitchen berry handler must find ripe bush");
        h.assertTrue(!(boolean)call(tasks.get(1).getSecond(),"checkExtraStartConditions",h,m,false) && m.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get()),"Berry work target must survive the walk to its adjacent standing point");
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(m.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().getTarget().currentBlockPosition())));
        h.assertTrue((boolean)call(tasks.get(1).getSecond(),"checkExtraStartConditions",h,m,false),"Kitchen berry handler must recognize local arrival");
        call(tasks.get(1).getSecond(),"start",h,m,true);
        h.assertTrue(h.getLevel().getBlockState(p).getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE)<3,"Fake-player berry interaction must harvest local bush");
        var feed=TaskManager.findTask(id("maidsoulkitchen:feed_animal_t")).orElseThrow();m.setTask(feed);
        var own=net.minecraft.world.entity.EntityType.SHEEP.create(h.getLevel());var foreign=net.minecraft.world.entity.EntityType.SHEEP.create(h.getLevel());
        own.setPos(m.position().add(0,0,1));foreign.setPos(m.position().add(0,0,-1));
        ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)own).sable$setTrackingSubLevel(s);
        ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)foreign).sable$setTrackingSubLevel(other);
        h.getLevel().addFreshEntity(own);h.getLevel().addFreshEntity(foreign);
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.WHEAT,8));
        m.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(m,List.of(foreign,own)));
        call(new com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidFeedAnimalTaskT(.5f,10),"start",h,m,true);
        h.assertTrue(own.isInLove() && !foreign.isInLove(),"Kitchen feeding must exclude sheep on a different structure");
        own.discard();foreign.discard();m.discard();CompatGameTests.cleanup(h,s,other);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void ordinaryWorldKitchenStillWorks(GameTestHelper h) throws ReflectiveOperationException {
        var m=new EntityMaid(h.getLevel());BlockPos floor=h.absolutePos(new BlockPos(3,2,3));
        for(BlockPos p:BlockPos.betweenClosed(floor.offset(-2,0,-2),floor.offset(2,0,2))) h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        m.setPos(Vec3.atBottomCenterOf(floor.above()));h.getLevel().addFreshEntity(m);
        m.getSchedulePos().setHomeModeEnable(m,m.blockPosition());m.setHomeModeEnable(true);m.getSchedulePos().restrictTo(m);
        BlockPos board=floor.offset(1,1,0);put(h,board,"farmersdelight:cutting_board");
        var task=TaskManager.findTask(id("maidsoulkitchen:fd_cutting_board")).orElseThrow();m.setTask(task);
        m.getMaidInv().setStackInSlot(0,new ItemStack(Items.ACACIA_LOG,2));m.getMaidInv().setStackInSlot(1,new ItemStack(Items.IRON_AXE));
        var tasks=task.createBrainTasks(m);call(tasks.getFirst().getSecond(),"searchForDestination",h,m,false);
        h.assertTrue(!Work.active(m) && m.getBrain().getMemory(InitEntities.TARGET_POS.get()).orElseThrow().currentBlockPosition().equals(board),"Ordinary-world search keeps original behavior");
        h.assertTrue((boolean)call(tasks.get(1).getSecond(),"checkExtraStartConditions",h,m,false),"Ordinary-world arrival still works");
        call(tasks.get(1).getSecond(),"start",h,m,true);call(tasks.get(1).getSecond(),"tick",h,m,true);
        h.assertTrue(!((vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity)h.getLevel().getBlockEntity(board)).getStoredItem().isEmpty(),"Ordinary-world task still inserts ingredients");
        m.discard();h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty")
    public static void seasonalFarmAndSnow(GameTestHelper h) throws ReflectiveOperationException {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);home(m);
        BlockPos p=s.getPlot().getCenterBlock().offset(4,1,3);
        var farm=TaskManager.findTask(id("maidsoulkitchen:eclipticseasons_farm")).orElseThrow();m.setTask(farm);
        h.getLevel().setBlock(p.below(),Blocks.FARMLAND.defaultBlockState(),2);
        h.getLevel().setBlock(p,Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7),2);
        var tasks=farm.createBrainTasks(m);call(tasks.getFirst().getSecond(),"searchForDestination",h,m,false);
        h.assertTrue(m.getBrain().getMemory(InitEntities.TARGET_POS.get()).isPresent(),"Seasonal farm must find a ripe crop");
        call(tasks.get(1).getSecond(),"start",h,m,true);
        h.assertTrue(!h.getLevel().getBlockState(p).is(Blocks.WHEAT) || h.getLevel().getBlockState(p).getValue(net.minecraft.world.level.block.CropBlock.AGE)<7,"Seasonal farm must harvest local crop (and may replant)");
        put(h,p,"minecraft:snow");var snow=TaskManager.findTask(id("eclipticseasons_multimodpatch:clean_snow")).orElseThrow();m.setTask(snow);
        m.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.teamtea.eclipticseasons.common.registry.ItemRegistry.broom.get()));
        var snowTasks=snow.createBrainTasks(m);call(snowTasks.getFirst().getSecond(),"searchForDestination2",h,m,false);
        h.assertTrue(m.getBrain().getMemory(InitEntities.TARGET_POS.get()).orElseThrow().currentBlockPosition().equals(p),"Seasonal broom must search on ship");
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(p)));
        h.assertTrue((boolean)call(snowTasks.get(1).getSecond(),"checkExtraStartConditions",h,m,false),"Seasonal broom arrival must use local coordinates");
        call(snowTasks.get(1).getSecond(),"start",h,m,true);
        h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Seasonal broom must remove snow");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
}
