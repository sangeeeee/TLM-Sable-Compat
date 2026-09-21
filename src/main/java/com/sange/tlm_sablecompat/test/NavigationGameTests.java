package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@PrefixGameTestTemplate(false)
public class NavigationGameTests {
    private static EntityMaid worldMaid(GameTestHelper h,BlockPos feet,int width) {
        for(BlockPos p:BlockPos.betweenClosed(feet.offset(-1,-1,-width),feet.offset(4,-1,width)))
            h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        var m=new EntityMaid(h.getLevel());m.setPos(Vec3.atBottomCenterOf(feet));m.setOnGround(true);
        m.setNoAi(true);m.setNoGravity(true);h.getLevel().addFreshEntity(m);return m;
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="navigation")
    public static void shortDetourAndSolidWall(GameTestHelper h) {
        BlockPos feet=h.absolutePos(new BlockPos(2,3,4));var m=worldMaid(h,feet,3);
        h.getLevel().setBlock(feet.east(),Blocks.OAK_FENCE.defaultBlockState(),2);
        var route=LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2)));
        h.assertTrue(route.size()==3,"A narrow post must permit a short collision-free lateral detour");
        h.assertTrue(Math.abs(route.getFirst().z-m.getZ())>.3,"Recovery must adjust the precise position, not round back to the blocked cell center");
        for(BlockPos p:BlockPos.betweenClosed(feet.offset(1,0,-3),feet.offset(1,2,3)))
            h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2))).isEmpty(),"A solid wall must not permit a through-wall detour");
        m.discard();h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="navigation")
    public static void supportAndHomeLimit(GameTestHelper h) {
        BlockPos feet=h.absolutePos(new BlockPos(2,3,4));var m=worldMaid(h,feet,0);
        h.getLevel().setBlock(feet.east(),Blocks.OAK_FENCE.defaultBlockState(),2);
        h.assertTrue(LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2))).isEmpty(),"Recovery must not walk around an obstacle by leaving a one-block ledge");
        for(BlockPos p:BlockPos.betweenClosed(feet.offset(-1,-1,-2),feet.offset(4,-1,2))) h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        m.setHomeModeEnable(true);m.restrictTo(feet,1);
        h.assertTrue(LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2))).isEmpty(),"A detour must respect the active ordinary-world Home restriction");
        m.discard();h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="navigation")
    public static void tiltedMovingDeck(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);
        BlockPos feet=s.getPlot().getCenterBlock().offset(2,1,3);
        h.getLevel().setBlock(feet.east(),Blocks.OAK_FENCE.defaultBlockState(),2);
        s.logicalPose().orientation().rotateY(.8).rotateZ(.06);s.logicalPose().position().add(30,4,20);
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(feet)).add(0,.08,0));
        Homes.setSimple(m);m.setHomeModeEnable(true);m.getSchedulePos().restrictTo(m);
        var route=LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2)));
        h.assertTrue(route.size()==3,"A translated, yawed and mildly tilted deck must support local detours");
        Vec3 here=Spaces.local(s,m.position());s.logicalPose().position().add(15,0,8);m.setPos(Spaces.world(s,here));
        var moved=LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2)));
        h.assertTrue(moved.size()==3 && moved.getFirst().distanceTo(route.getFirst())<.001,"Ship translation must preserve the local detour");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="navigation",timeoutTicks=80)
    public static void realNavigationRecoveryAndNewObstacle(GameTestHelper h) {
        BlockPos feet=h.absolutePos(new BlockPos(2,3,4));var m=worldMaid(h,feet,3);
        h.getLevel().setBlock(feet.east(),Blocks.OAK_FENCE.defaultBlockState(),2);
        BlockPos goal=feet.east(2);Vec3 start=m.position();
        // Simulate a previously computed grid route whose next node has become obstructed.
        Path path=new Path(List.of(new Node(feet.getX()+1,feet.getY(),feet.getZ()),
                new Node(goal.getX(),goal.getY(),goal.getZ())),goal,true);
        m.getNavigation().moveTo(path,.5);
        var walk=new WalkTarget(goal,.5f,0);m.getBrain().setMemory(MemoryModuleType.WALK_TARGET,walk);
        m.getNavigation().tick();
        h.runAtTickTime(22,() -> {
            m.setOnGround(true);m.getNavigation().tick();
            var control=m.getMoveControl();
            h.assertTrue(Math.abs(control.getWantedZ()-start.z)>.3,"Real navigation tick must issue the precise sidestep after a stall");
            h.assertTrue(m.position().distanceTo(start)<.001,"Recovery must never teleport the maid");
            h.assertTrue(m.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()==walk,"Work/follow walk target must remain unchanged");
            // Block both side exits after the cached plan was produced.
            for(int z:new int[]{-1,1}) for(int y=0;y<2;y++) h.getLevel().setBlock(feet.offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
        });
        h.runAtTickTime(28,() -> {
            m.setOnGround(true);m.getNavigation().tick();
            h.assertTrue(Math.abs(m.getMoveControl().getWantedZ()-start.z)<.01,"A new obstruction must cancel the cached detour and release normal navigation");
            m.discard();h.succeed();
        });
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="navigation",timeoutTicks=80)
    public static void movingShipStillDetectsLocalStall(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);
        BlockPos feet=s.getPlot().getCenterBlock().offset(2,1,3),goal=feet.east(2);
        h.getLevel().setBlock(feet.east(),Blocks.OAK_FENCE.defaultBlockState(),2);
        m.setNoAi(true);m.setNoGravity(true);
        Vec3 local=Vec3.atBottomCenterOf(feet).add(0,.025,0);m.setPos(Spaces.world(s,local));m.setOnGround(true);
        var path=new Path(List.of(new Node(goal.getX(),goal.getY(),goal.getZ())),goal,true);
        ((dev.ryanhcode.sable.mixinterface.entity.pathfinding.PathExtension)path).sable$setLocalPath(h.getLevel(),true);
        m.getNavigation().moveTo(path,.5);m.getNavigation().tick();
        h.runAtTickTime(22,() -> {
            s.logicalPose().position().add(30,10,20);s.logicalPose().orientation().identity().rotateY(.7);
            ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)m).sable$setTrackingSubLevel(s);
            m.setPos(Spaces.world(s,local));m.setOnGround(true);m.getNavigation().tick();
            var control=m.getMoveControl();
            Vec3 wanted=Spaces.local(s,new Vec3(control.getWantedX(),control.getWantedY(),control.getWantedZ()));
            h.assertTrue(Math.abs(wanted.z-local.z)>.3 && wanted.distanceTo(local)<2,"Ship motion must not conceal a local stall; wanted="+wanted+", local="+local+", done="+path.isDone()+", plan="+LocalAvoidance.plan(m,Vec3.atBottomCenterOf(goal)));
            h.assertTrue(Spaces.local(s,m.position()).distanceTo(local)<.001,"Ship recovery must not teleport");
            m.getNavigation().stop();
            h.assertTrue(!LocalAvoidance.tick(m,null,.5),"Stopping navigation must immediately cancel a pending detour");
            m.discard();CompatGameTests.cleanup(h,s);h.succeed();
        });
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="navigation")
    public static void compassCommuteCanDetour(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1);var m=CompatGameTests.maid(h,s);
        BlockPos base=s.getPlot().getCenterBlock(),feet=base.offset(1,1,3),destination=base.offset(6,1,3);
        h.getLevel().setBlock(feet.east(),Blocks.OAK_FENCE.defaultBlockState(),2);
        m.setPos(Spaces.world(s,Vec3.atBottomCenterOf(feet)));
        var binding=new Binding(true);binding.touchedStructure=true;
        for(int i=0;i<3;i++) binding.points.add(Spaces.point(h.getLevel(),s,
                i==Homes.activity(m)?destination:feet,i==Homes.activity(m)?destination.below():feet.below()));
        Homes.attach(m,BindingStore.get(h.getLevel()).add(binding));m.setHomeModeEnable(true);
        m.getSchedulePos().setConfigured(true);m.restrictTo(destination,2);
        h.assertTrue(!Work.blockAllowed(m,feet),"Fixture starts outside the current compass area");
        h.assertTrue(LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2))).isEmpty(),"Outside-area work must not acquire an unrestricted detour");
        m.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(destination,.7f,1));
        h.assertTrue(!LocalAvoidance.plan(m,Vec3.atBottomCenterOf(feet.east(2))).isEmpty(),"An existing commute into the selected compass area may detour on its own deck");
        h.assertTrue(!Work.blockAllowed(m,feet),"Commute recovery must not widen the work search area");
        m.discard();CompatGameTests.cleanup(h,s);h.succeed();
    }
    @GameTest(templateNamespace="tlm_sablecompat",template="empty",batch="foreign_obstacle",timeoutTicks=60)
    public static void foreignStructureBlocksDetour(GameTestHelper h) {
        BlockPos feet=h.absolutePos(new BlockPos(2,3,4));var m=worldMaid(h,feet,3);
        h.getLevel().setBlock(feet.east(),Blocks.OAK_FENCE.defaultBlockState(),2);
        Vec3 goal=Vec3.atBottomCenterOf(feet.east(2));
        h.assertTrue(!LocalAvoidance.plan(m,goal).isEmpty(),"Unobstructed side route must initially exist");
        var blocks=new java.util.ArrayList<BlockPos>();
        for(BlockPos p:BlockPos.betweenClosed(feet.offset(1,0,-3),feet.offset(1,1,3))) {
            h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);blocks.add(p.immutable());
        }
        var other=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),feet.east(),blocks,
                java.util.Objects.requireNonNull(dev.ryanhcode.sable.companion.math.BoundingBox3i.from(blocks)).expand(1,1,1));
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(feet.east()).isAir(),"The obstructing wall must have left the ordinary-world block grid");
            var nearby=new java.util.ArrayList<>();dev.ryanhcode.sable.Sable.HELPER.getAllIntersecting(h.getLevel(),new dev.ryanhcode.sable.companion.math.BoundingBox3d(m.getBoundingBox().inflate(4,1,4))).forEach(nearby::add);
            h.assertTrue(nearby.contains(other),"Wait for Sable's asynchronous physics broad phase to publish the new wall");
            h.assertTrue(LocalAvoidance.plan(m,goal).isEmpty(),"Collision checks must include a different physical structure; nearby="+nearby.size()+", bounds="+other.boundingBox()+", wall="+Spaces.world(other,other.getPlot().getCenterBlock().getCenter())+", maid="+m.position());
            m.discard();CompatGameTests.cleanup(h,other);h.succeed();
        });
    }
}
