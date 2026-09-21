package com.sange.tlm_sablecompat.test;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.hmtl.tlmmaidmanager.compat.sable.SableCompat;
import com.hmtl.tlmmaidmanager.compat.sable.WorkBlockManagedHomeData;
import com.hmtl.tlmmaidmanager.init.InitBlocks;
import com.hmtl.tlmmaidmanager.init.InitItems;
import com.hmtl.tlmmaidmanager.network.MaidTeleporter;
import com.hmtl.tlmmaidmanager.network.message.TeleportMaidToWorkBlockPacket;
import com.hmtl.tlmmaidmanager.world.WorkBlockData;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.lang.reflect.Proxy;
import java.util.*;

@PrefixGameTestTemplate(false)
public class ManagerGameTests {
    private static BlockPos anchor(GameTestHelper h, ServerSubLevel s) {
        BlockPos p = s.getPlot().getCenterBlock().offset(3, 1, 3);
        h.getLevel().setBlockAndUpdate(p, InitBlocks.WORK_BLOCK.get().defaultBlockState());
        return p;
    }
    private static WorkBlockData.Entry entry(GameTestHelper h, BlockPos p) {
        return new WorkBlockData.Entry("test-anchor", h.getLevel().dimension().location().toString(), p.getX(), p.getY(), p.getZ());
    }
    private static void move(GameTestHelper h, ServerSubLevel s) {
        var pose = s.logicalPose();
        ServerSubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline().teleport(s,
                new org.joml.Vector3d(pose.position()).add(42, 25, 33),
                new org.joml.Quaterniond(pose.orientation()).rotateY(.8).rotateX(.04));
    }
    private static void dispatch(GameTestHelper h, ServerPlayer player, EntityMaid maid, BlockPos p) {
        try {
            IPayloadContext context = (IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                    new Class[]{IPayloadContext.class}, (proxy, method, args) -> {
                        if (method.getName().equals("player")) return player;
                        throw new UnsupportedOperationException(method.getName());
                    });
            var method = TeleportMaidToWorkBlockPacket.class.getDeclaredMethod("lambda$handle$0", IPayloadContext.class, TeleportMaidToWorkBlockPacket.class);
            method.setAccessible(true);
            method.invoke(null, context, new TeleportMaidToWorkBlockPacket(maid.getUUID(), h.getLevel().dimension().location().toString(), p.getX(), p.getY(), p.getZ()));
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    @GameTest(templateNamespace="tlm_sablecompat", template="empty")
    public static void movingAnchorPacketAndHome(GameTestHelper h) {
        var a = CompatGameTests.platform(h, 1); var b = CompatGameTests.platform(h, 10);
        var maid = CompatGameTests.maid(h, a); maid.setNoAi(true);
        var player = CompatGameTests.player(h); maid.setOwnerUUID(player.getUUID()); CompatGameTests.OWNERS.put(maid, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, InitItems.MAID_MANAGER.get().getDefaultInstance());
        BlockPos p = anchor(h, b);
        WorkBlockData.get(h.getLevel()).addName(h.getLevel(), p, "moving", player.getUUID());
        var list = SableCompat.toListEntry(h.getLevel(), WorkBlockData.get(h.getLevel()).getNamedEntry(h.getLevel(), p, player.getUUID()));
        move(h, b);
        h.assertTrue(new BlockPos(list.x(), list.y(), list.z()).equals(p), "UI address must survive movement after opening");
        dispatch(h, player, maid, p);
        h.assertTrue(Spaces.tracking(maid) == b, "Real packet must transfer A to B");
        h.assertTrue(Spaces.local(b, maid.position()).distanceTo(p.getCenter()) < 5, "Arrival must be near current anchor pose");
        var binding = Homes.binding(maid);
        h.assertTrue(binding != null && binding.points.getFirst().anchor().equals(p), "Packet must preserve anchor Home, not projected world Home");
        h.assertTrue(maid.isHomeModeEnable(), "Manager dispatch enables Home");
        SableCompat.registerManagedHome(h.getLevel(), maid, p, maid.blockPosition());
        h.assertTrue(WorkBlockManagedHomeData.get(h.getLevel()).isEmpty(), "No competing projected Home updater");
        Vec3 local = Spaces.local(b, maid.position()); move(h, b); maid.setPos(Spaces.world(b, local)); Homes.tick(maid);
        h.assertTrue(maid.getSchedulePos().getWorkPos().equals(p.above()) && Homes.status(h.getLevel(), binding).isEmpty(), "Home remains local after second move");
        maid.discard(); player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        CompatGameTests.cleanup(h, a, b); h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat", template="empty")
    public static void unsafeAndMissingAnchorDoNotMove(GameTestHelper h) {
        var s = CompatGameTests.platform(h, 1); var maid = CompatGameTests.maid(h, s); maid.setNoAi(true);
        BlockPos p = anchor(h, s); Homes.setSimple(maid); var oldHome = Homes.id(maid); Vec3 oldPos = maid.position();
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-3, 0, -3), p.offset(3, 5, 3)))
            if (!q.equals(p)) h.getLevel().setBlock(q, Blocks.STONE.defaultBlockState(), 2);
        h.assertTrue(SableCompat.teleportMaidToWorkBlock(maid, h.getLevel(), p) == SableCompat.Result.FAILED_PLOT, "Enclosed anchor must fail rather than use unsafe fallback");
        h.assertTrue(maid.position().equals(oldPos) && Objects.equals(oldHome, Homes.id(maid)), "Failure must preserve position and Home");
        h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        h.assertTrue(!MaidTeleporter.teleportMaidToWorkBlockForTask(maid, entry(h,p)), "Schedule dispatch must reject removed anchor");
        CompatGameTests.cleanup(h,s);
        h.assertTrue(SableCompat.teleportMaidToWorkBlock(maid,h.getLevel(),p)==SableCompat.Result.FAILED_PLOT, "Removed structure must not be restored from a stale pointer");
        maid.discard(); h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat", template="empty")
    public static void compassConflictAndTilt(GameTestHelper h) {
        var a = CompatGameTests.platform(h,1); var b = CompatGameTests.platform(h,10);
        var maid = CompatGameTests.maid(h,a); maid.setNoAi(true); BlockPos pa=anchor(h,a), pb=anchor(h,b);
        Binding compass = new Binding(true); compass.touchedStructure=true;
        compass.points.add(Spaces.point(h.getLevel(),a,pa.above(),pa));
        Homes.attach(maid,BindingStore.get(h.getLevel()).add(compass)); maid.getSchedulePos().setConfigured(true);
        h.assertTrue(SableCompat.teleportMaidToWorkBlock(maid,h.getLevel(),pb)==SableCompat.Result.FAILED_PLOT,"Must not erase or violate another ship's compass");
        h.assertTrue(MaidTeleporter.teleportMaidToWorkBlockForTask(maid,entry(h,pa)),"Same-space compass can dispatch");
        h.assertTrue(Homes.binding(maid)==compass && maid.getSchedulePos().isConfigured(),"Same-space compass preserved");
        maid.getSchedulePos().setConfigured(false);
        b.logicalPose().orientation().rotateZ(Math.PI/2);
        h.assertTrue(!MaidTeleporter.teleportMaidToWorkBlockForTask(maid,entry(h,pb)),"Sideways anchor must fail");
        maid.discard(); CompatGameTests.cleanup(h,a,b); h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat", template="empty")
    public static void namedAnchorAssemblyAndSerialization(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(2,3,2));
        h.getLevel().setBlockAndUpdate(p,InitBlocks.WORK_BLOCK.get().defaultBlockState());
        var player=CompatGameTests.player(h); var data=WorkBlockData.get(h.getLevel());
        data.addName(h.getLevel(),p,"assembled",player.getUUID());
        var s=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),p,List.of(p),Objects.requireNonNull(BoundingBox3i.from(List.of(p))).expand(1,1,1));
        BlockPos local=s.getPlot().getCenterBlock();
        var e=data.getNamedEntry(h.getLevel(),local,player.getUUID());
        h.assertTrue(e!=null && new BlockPos(e.x(),e.y(),e.z()).equals(local),"Assembly must move both key and serialized coordinates");
        var restored=WorkBlockData.load(data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.getNamedEntry(h.getLevel(),local,player.getUUID())!=null,"Anchor survives save/load at new address");
        h.assertTrue(SableCompat.findWorkBlockByPos(h.getLevel(),local,CompatGameTests.player(h))==null,"Other owners cannot use the named entry");
        CompatGameTests.cleanup(h,s); h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat", template="empty")
    public static void worldAndCrossDimensionAnchors(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1); var maid=CompatGameTests.maid(h,s); maid.setNoAi(true);
        BlockPos world=h.absolutePos(new BlockPos(10,3,3)); h.getLevel().setBlockAndUpdate(world,InitBlocks.WORK_BLOCK.get().defaultBlockState());
        h.assertTrue(MaidTeleporter.teleportMaidToWorkBlockForTask(maid,entry(h,world)),"Structure to normal-world anchor");
        h.assertTrue(Spaces.tracking(maid)==null && Homes.binding(maid).points.getFirst().structure()==null,"World dispatch clears old ship tracking and Home");
        var other=h.getLevel().getServer().getLevel(Level.NETHER); BlockPos target=new BlockPos(32,200,32);
        other.getChunkAt(target);
        var old=other.getBlockState(target); var above=other.getBlockState(target.above()); var above2=other.getBlockState(target.above(2));
        other.setBlockAndUpdate(target,InitBlocks.WORK_BLOCK.get().defaultBlockState()); other.setBlockAndUpdate(target.above(),Blocks.AIR.defaultBlockState()); other.setBlockAndUpdate(target.above(2),Blocks.AIR.defaultBlockState());
        h.assertTrue(SableCompat.teleportMaidToWorkBlock(maid,other,target)==SableCompat.Result.ENTERED,"Cross-dimension anchor must succeed");
        var arrived=(EntityMaid)other.getEntity(maid.getUUID());
        h.assertTrue(arrived!=null && !arrived.isRemoved() && maid.isRemoved(),"Replacement maid must enter destination level");
        h.assertTrue(Homes.binding(arrived).points.getFirst().dimension().equals(other.dimension().location().toString()),"Home must use destination dimension");
        BlockPos local=anchor(h,s); move(h,s);
        h.assertTrue(SableCompat.teleportMaidToWorkBlock(arrived,h.getLevel(),local)==SableCompat.Result.ENTERED,"Cross-dimension return onto moving structure");
        var returned=(EntityMaid)h.getLevel().getEntity(arrived.getUUID());
        h.assertTrue(returned!=null && Spaces.tracking(returned)==s && Homes.binding(returned).points.getFirst().anchor().equals(local),"Replacement receives tracking and local Home");
        returned.discard(); other.setBlockAndUpdate(target,old); other.setBlockAndUpdate(target.above(),above); other.setBlockAndUpdate(target.above(2),above2);
        h.getLevel().setBlockAndUpdate(world,Blocks.AIR.defaultBlockState()); CompatGameTests.cleanup(h,s); h.succeed();
    }

    @GameTest(templateNamespace="tlm_sablecompat", template="empty")
    public static void splitAnchorFollowsExactMovedBlock(GameTestHelper h) {
        var s=CompatGameTests.platform(h,1); var maid=CompatGameTests.maid(h,s); maid.setNoAi(true);
        BlockPos p=anchor(h,s); var player=CompatGameTests.player(h); var data=WorkBlockData.get(h.getLevel());
        data.addName(h.getLevel(),p,"fragment",player.getUUID());
        h.assertTrue(MaidTeleporter.teleportMaidToWorkBlockForTask(maid,entry(h,p)),"Initial dispatch");
        move(h,s);
        var fragment=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),p,List.of(p),Objects.requireNonNull(BoundingBox3i.from(List.of(p))).expand(1,1,1));
        BlockPos now=fragment.getPlot().getCenterBlock();
        var saved=data.getNamedEntry(h.getLevel(),now,player.getUUID());
        h.assertTrue(saved!=null && new BlockPos(saved.x(),saved.y(),saved.z()).equals(now),"Split updates exact named anchor address");
        h.assertTrue(data.getNamedEntry(h.getLevel(),p,player.getUUID())==null,"Old fragment address removed");
        h.assertTrue(Homes.binding(maid).points.getFirst().anchor().equals(now),"Existing Home follows anchor fragment");
        h.assertTrue(MaidTeleporter.teleportMaidToWorkBlockForTask(maid,saved),"Schedule can dispatch to fragment");
        h.assertTrue(Spaces.tracking(maid)==fragment,"Maid arrives on the correct fragment");
        maid.discard(); CompatGameTests.cleanup(h,s,fragment); h.succeed();
    }
}
