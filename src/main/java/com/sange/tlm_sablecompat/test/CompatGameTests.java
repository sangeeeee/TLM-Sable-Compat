package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemKappaCompass;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Loaded only by the explicitly enabled development GameTest run. */
@PrefixGameTestTemplate(false)
public class CompatGameTests {
    private static final Map<EntityMaid, ServerPlayer> OWNERS = new WeakHashMap<>();
    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "compat-test"));
    }
    private static ServerSubLevel platform(GameTestHelper h, int offset) {
        var blocks = new ArrayList<BlockPos>();
        BlockPos origin = h.absolutePos(new BlockPos(offset, 3, 1));
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            BlockPos p = origin.offset(x, 0, z); h.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState()); blocks.add(p);
        }
        return SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), origin, blocks, Objects.requireNonNull(BoundingBox3i.from(blocks)).expand(1,1,1));
    }
    private static EntityMaid maid(GameTestHelper h, ServerSubLevel s) {
        EntityMaid m = new EntityMaid(h.getLevel()) {
            @Override public net.minecraft.world.entity.LivingEntity getOwner() { return OWNERS.get(this); }
        };
        BlockPos p = s.getPlot().getCenterBlock().offset(3, 1, 3);
        m.setPos(Spaces.world(s, Vec3.atBottomCenterOf(p)));
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(s);
        m.setOnGround(true);
        h.getLevel().addFreshEntity(m);
        return m;
    }
    private static Binding group(GameTestHelper h, ServerSubLevel s, boolean compass) {
        Binding b = new Binding(compass); b.touchedStructure = true;
        BlockPos a = s.getPlot().getCenterBlock();
        b.points.add(Spaces.point(h.getLevel(), s, a.above(), a)); return b;
    }
    private static void cleanup(GameTestHelper h, ServerSubLevel... structures) {
        for (var s : structures) if (!s.isRemoved()) SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(s, SubLevelRemovalReason.REMOVED);
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void homeAndNavigation(GameTestHelper h) {
        var s = platform(h, 1); var m = maid(h, s);
        m.getSchedulePos().setHomeModeEnable(m, m.blockPosition());
        m.setHomeModeEnable(true); m.getSchedulePos().restrictTo(m);
        h.assertTrue(Homes.binding(m) != null && Homes.binding(m).points.getFirst().structure().equals(s.getUniqueId()), "GUI Home must bind tracked structure");
        h.assertTrue(m.isWithinRestriction(), "Maid at its structure Home must be inside restriction");
        Vec3 oldLocal = Spaces.local(s, m.position());
        s.logicalPose().position().add(70, 0, 30);
        m.setPos(Spaces.world(s, oldLocal));
        h.assertTrue(m.isWithinRestriction(), "Translation must not move Home relative to maid");
        var path = m.getNavigation().createPath(s.getPlot().getCenterBlock().offset(5,1,5), 0);
        h.assertTrue(path != null && path.canReach(), "Same-structure path must reach destination");
        Vec3 next = path.getNextEntityPos(m);
        h.assertTrue(next.distanceTo(m.position()) < 12, "Path waypoint must be projected into world space");
        m.setHomeModeEnable(false);
        var bfs = new MaidPathFindingBFS(m.getNavigation().getNodeEvaluator(), h.getLevel(), m);
        h.assertTrue(bfs.canPathReach(s.getPlot().getCenterBlock().offset(4,0,4)), "BFS region and start must both use plot coordinates");
        bfs.finish(); m.discard(); cleanup(h,s); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void splitAndRemoval(GameTestHelper h) {
        var s = platform(h, 1);
        Binding b = group(h,s,true);
        BlockPos second = s.getPlot().getCenterBlock().offset(6,0,6);
        b.points.add(Spaces.point(h.getLevel(),s,second.above(),second));
        var store = BindingStore.get(h.getLevel()); UUID id = store.add(b);
        var split = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), second, List.of(second),
                Objects.requireNonNull(BoundingBox3i.from(List.of(second))).expand(1,1,1));
        store.settle();
        h.assertTrue(store.find(id).failure.equals("split"), "Split across two structures must invalidate all areas");
        h.assertTrue(store.find(id).points.get(1).structure().equals(split.getUniqueId()), "Exact moved anchor must migrate");
        Binding restored = Binding.load(store.find(id).save());
        h.assertTrue(restored.failure.equals("split"), "Invalidation must survive serialization");
        Binding removed = group(h,split,true); UUID removedId = store.add(removed);
        cleanup(h,split);
        h.assertTrue(store.find(removedId).failure.equals("removed"), "Normal Sable removal must revoke unloaded-item bindings");
        cleanup(h,s); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void assemblyMigration(GameTestHelper h) {
        BlockPos anchor = h.absolutePos(new BlockPos(2,3,2));
        h.getLevel().setBlockAndUpdate(anchor,Blocks.STONE.defaultBlockState());
        Binding b = new Binding(false); b.points.add(Spaces.point(h.getLevel(),null,anchor.above(),anchor));
        BindingStore.get(h.getLevel()).add(b);
        var s = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),anchor,List.of(anchor),
                Objects.requireNonNull(BoundingBox3i.from(List.of(anchor))).expand(1,1,1));
        h.assertTrue(s.getUniqueId().equals(b.points.getFirst().structure()),"World Home must migrate when its block is assembled");
        h.assertTrue(b.points.getFirst().position().equals(s.getPlot().getCenterBlock().above()),"Home offset must survive assembly");
        cleanup(h,s); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void bindingValidation(GameTestHelper h) {
        var a = platform(h,1); var b = platform(h,10); var m = maid(h,a);
        Binding binding = group(h,a,true);
        h.assertTrue(Homes.validateMaid(m,binding).isEmpty(),"Nearby maid on the same structure should bind");
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(b);
        h.assertTrue(Homes.validateMaid(m,binding).equals("other_structure"),"Wrong structure must precede distance failure");
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(null);
        h.assertTrue(Homes.validateMaid(m,binding).equals("not_on_structure"),"Untracked maid must be rejected");
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(a);
        m.setPos(Spaces.world(a,binding.points.getFirst().position().getCenter().add(80,0,0)));
        h.assertTrue(Homes.validateMaid(m,binding).equals("far"),"Distant maid must be rejected");
        m.discard(); cleanup(h,a,b); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void unsafeAndTilted(GameTestHelper h) {
        var a = platform(h,1); var m = maid(h,a); BlockPos feet = a.getPlot().getCenterBlock().offset(3,1,3);
        h.assertTrue(Spaces.landing(m,a,feet) != null,"Clear deck must offer a landing");
        h.getLevel().setBlockAndUpdate(feet,Blocks.STONE.defaultBlockState());
        h.assertTrue(Spaces.landing(m,a,feet) == null,"Landing inside a block must be rejected");
        h.getLevel().setBlockAndUpdate(feet,Blocks.AIR.defaultBlockState());
        a.logicalPose().orientation().rotateZ(Math.PI/2);
        h.assertTrue(!Spaces.upright(a) && Spaces.landing(m,a,feet) == null,"Sideways ship must suspend landing");
        a.logicalPose().orientation().identity();
        h.assertTrue(Spaces.upright(a),"Righted structure should recover");
        h.assertTrue(!Spaces.teleportNear(m,null,m.position().add(0,100,0),true),"Flying owner's empty world position must not yield a landing");
        m.discard(); cleanup(h,a); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void compassAtomicity(GameTestHelper h) {
        var a = platform(h,1); var b = platform(h,10);
        ServerPlayer player = player(h);
        var m = maid(h,a); m.tame(player); OWNERS.put(m,player);
        ItemStack compass = new ItemStack(InitItems.KAPPA_COMPASS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,compass);
        var item = (ItemKappaCompass)compass.getItem();
        BlockPos pos = a.getPlot().getCenterBlock();
        item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false)));
        UUID original = Compasses.id(compass);
        BlockPos wrong = b.getPlot().getCenterBlock();
        item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(wrong.getCenter(),Direction.UP,wrong,false)));
        h.assertTrue(original.equals(Compasses.id(compass)) && ItemKappaCompass.getRecordCount(compass)==1,"Rejected record must leave compass unchanged");
        item.interactLivingEntity(compass,player,m,InteractionHand.MAIN_HAND);
        UUID bound = Homes.id(m);
        h.assertTrue(bound != null && !bound.equals(original),"Maid gets an independent configuration copy");
        ((EntityMovementExtension)m).sable$setTrackingSubLevel(b);
        item.interactLivingEntity(compass,player,m,InteractionHand.MAIN_HAND);
        h.assertTrue(bound.equals(Homes.id(m)),"Rejected binding must preserve old Home");
        m.discard(); cleanup(h,a,b); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void waitPreservesOtherWork(GameTestHelper h) {
        var a = platform(h,1); var m = maid(h,a); ServerPlayer player = player(h); m.tame(player); OWNERS.put(m,player);
        player.setPos(m.position().add(0,100,0));
        ((EntityMovementExtension)player).sable$setTrackingSubLevel(null);
        WalkTarget work = new WalkTarget(new BlockPosTracker(a.getPlot().getCenterBlock().offset(2,1,2)),0.5f,1);
        m.getBrain().setMemory(MemoryModuleType.WALK_TARGET,work);
        h.assertTrue(Navigation.follow(m),"Sable follow branch should handle departure");
        h.assertTrue(m.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null)==work,"Failed follow teleport must preserve independent work target");
        h.assertTrue(Spaces.tracking(m)==a,"Flying over an unselected structure must not choose it");
        m.discard(); cleanup(h,a); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void selectedStructureFollow(GameTestHelper h) {
        var a = platform(h,1); var b = platform(h,10); var m = maid(h,a);
        var owner = player(h); m.tame(owner); OWNERS.put(m,owner);
        owner.setPos(Spaces.world(b,Vec3.atBottomCenterOf(b.getPlot().getCenterBlock().offset(3,1,3))));
        m.getRandom().setSeed(342);
        for (int i = 0; i < 20; i++) { Navigation.state(m).retryAt = 0; Navigation.follow(m); }
        h.assertTrue(Spaces.tracking(m)==a,"Flying above B without Sable tracking must never select B");
        ((EntityMovementExtension)owner).sable$setTrackingSubLevel(b);
        for (int i = 0; i < 20 && Spaces.tracking(m)!=b; i++) { Navigation.state(m).retryAt = 0; Navigation.follow(m); }
        h.assertTrue(Spaces.tracking(m)==b,"Owner tracked on B must allow safe transfer from A");
        h.assertTrue(Spaces.local(b,m.position()).distanceTo(Spaces.local(b,owner.position()))<6,"Transfer must land near owner on B");
        BlockPos ground = h.absolutePos(new BlockPos(4,30,4));
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) h.getLevel().setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
        owner.setPos(Vec3.atBottomCenterOf(ground.above()));
        ((EntityMovementExtension)owner).sable$setTrackingSubLevel(null);
        for (int i = 0; i < 20 && Spaces.tracking(m)!=null; i++) { Navigation.state(m).retryAt = 0; Navigation.follow(m); }
        h.assertTrue(Spaces.tracking(m)==null && m.position().distanceTo(owner.position())<6,"Owner returning to world ground must allow world landing");
        m.discard(); cleanup(h,a,b); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void otherStructureCollision(GameTestHelper h) {
        var a = platform(h,1); var b = platform(h,10); var m = maid(h,a);
        BlockPos feet = a.getPlot().getCenterBlock().offset(3,1,3);
        Vec3 obstacle = Spaces.world(b,Vec3.atCenterOf(b.getPlot().getCenterBlock()));
        Vec3 destination = Spaces.world(a,Vec3.atCenterOf(feet));
        Vec3 shift = destination.subtract(obstacle);
        b.logicalPose().position().add(shift.x,shift.y,shift.z);
        h.assertTrue(Spaces.landing(m,a,feet)==null,"Overlapping independent structure must block teleport landing");
        b.logicalPose().position().add(0,30,0);
        h.assertTrue(Spaces.landing(m,a,feet)!=null,"Removing obstacle must restore landing on A");
        m.discard(); cleanup(h,a,b); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void unloadAndWholeGroupMigration(GameTestHelper h) {
        var a = platform(h,1); var store = BindingStore.get(h.getLevel());
        Binding group = group(h,a,true);
        BlockPos second = a.getPlot().getCenterBlock().offset(1,0,0);
        group.points.add(Spaces.point(h.getLevel(),a,second,second));
        UUID id = store.add(group);
        var moved = List.of(group.points.getFirst().anchor(),second);
        var b = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),moved.getFirst(),moved,
                Objects.requireNonNull(BoundingBox3i.from(moved)).expand(1,1,1));
        store.settle();
        h.assertTrue(group.failure.isEmpty() && group.points.stream().allMatch(p->b.getUniqueId().equals(p.structure())),"Entire group migrating together must remain valid");
        SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(b,SubLevelRemovalReason.UNLOADED);
        h.assertTrue(group.failure.isEmpty() && Homes.status(h.getLevel(),group).equals("unloaded"),"Chunk unload must suspend, never revoke Home");
        store.removed(h.getLevel().dimension().location().toString(),b.getUniqueId());
        try {
            var loader = BindingStore.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);
            loader.setAccessible(true);
            var restored = (BindingStore)loader.invoke(null,store.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.find(id).failure.equals("removed"),"Removal tombstone must survive complete ledger reload");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        cleanup(h,a); h.succeed();
    }
    @GameTest(templateNamespace = "tlm_sablecompat", template = "empty")
    public static void brainArrivalAndLook(GameTestHelper h) {
        var a = platform(h,1); var m = maid(h,a);
        BlockPos target = Spaces.localPosition(m);
        WalkTarget walk = new WalkTarget(target,0.5f,1);
        try {
            var method = net.minecraft.world.entity.ai.behavior.MoveToTargetSink.class.getDeclaredMethod("reachedTarget",net.minecraft.world.entity.Mob.class,WalkTarget.class);
            method.setAccessible(true);
            var sink = new net.minecraft.world.entity.ai.behavior.MoveToTargetSink();
            h.assertTrue((Boolean)method.invoke(sink,m,walk),"Brain must recognize local destination arrival");
            a.logicalPose().position().add(100,0,50);
            a.logicalPose().orientation().rotateY(Math.PI/2);
            m.setPos(Spaces.world(a,Vec3.atBottomCenterOf(target)));
            h.assertTrue((Boolean)method.invoke(sink,m,walk),"Arrival must survive ship translation and yaw");
            Vec3 look = target.offset(2,1,0).getCenter();
            m.getLookControl().setLookAt(look);
            Vec3 actual = new Vec3(m.getLookControl().getWantedX(),m.getLookControl().getWantedY(),m.getLookControl().getWantedZ());
            h.assertTrue(actual.distanceTo(Spaces.world(a,look))<0.001,"Look target must use projected world coordinates");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        m.discard(); cleanup(h,a); h.succeed();
    }
}
