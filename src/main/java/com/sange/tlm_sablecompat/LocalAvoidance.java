package com.sange.tlm_sablecompat;

import com.github.tartaricacid.touhoulittlemaid.datagen.tag.TagBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Small, bounded recovery of an existing route. Never teleports or replaces a work target. */
public final class LocalAvoidance {
    private LocalAvoidance() {}
    private static final Map<EntityMaid, State> STATES = new WeakHashMap<>();
    private static final Vec3[] AXES = {new Vec3(1,0,0), new Vec3(0,1,0), new Vec3(0,0,1)};
    private static final class State {
        UUID frame;
        BlockPos target;
        Vec3 sample, progress;
        long sampleAt, retryAt, expires, checkAt;
        int failures, routeIndex, resumeIndex;
        Path path;
        List<Vec3> route = List.of();
    }
    public static void clear(EntityMaid m) { STATES.remove(m); }

    /** Called before vanilla navigation tick. True means a temporary precise move owns this tick. */
    public static boolean tick(EntityMaid m, Path path, double speed) {
        if (m.level().isClientSide() || !(m.getNavigation() instanceof GroundPathNavigation)
                || speed <= 0 || !m.canBrainMoving()
                || m.isPassenger() || m.isSleeping() || !m.onGround() || m.isInWaterOrBubble()
                || Work.active(m) && !Work.ready(m)) { clear(m); return false; }
        SubLevel s = Spaces.tracking(m);
        if (path == null || path.isDone()) {
            State paused=STATES.get(m);
            if(paused!=null) { paused.route=List.of(); paused.sample=Spaces.local(s,m.position()); paused.sampleAt=m.level().getGameTime(); }
            return false;
        }
        if (!Spaces.upright(s) || !Spaces.belongs(m.level(), path.getTarget(), s)) { clear(m); return false; }
        long now = m.level().getGameTime();
        Vec3 here = Spaces.local(s, m.position());
        UUID frame = s == null ? null : s.getUniqueId();
        State st = STATES.computeIfAbsent(m, unused -> new State());
        if (st.sample == null || !Objects.equals(frame,st.frame) || !path.getTarget().equals(st.target)) {
            st.frame=frame; st.target=path.getTarget(); st.sample=here; st.progress=here;
            st.sampleAt=now; st.retryAt=now+20; st.route=List.of(); st.failures=0;
        }
        // Path replacements invalidate only the short detour, not the retry budget.
        if (st.path != path) { st.path=path; st.route=List.of(); }
        if (!st.route.isEmpty()) {
            if (now >= st.expires) { failed(m,st,now); return false; }
            Vec3 next = st.route.get(st.routeIndex);
            if (here.distanceToSqr(next)<.025) {
                if (++st.routeIndex == st.route.size()) {
                    path.setNextNodeIndex(Math.max(path.getNextNodeIndex(),Math.min(st.resumeIndex,path.getNodeCount()-1)));
                    st.route=List.of(); st.sample=here; st.sampleAt=now; st.retryAt=now+40;
                    return false;
                }
                next=st.route.get(st.routeIndex);
            }
            // Moving obstacles are rechecked only while recovering. Normal collision remains authoritative.
            if (now >= st.checkAt) {
                st.checkAt=now+5;
                if (!new Corridor(m,s).segment(here,next)) { failed(m,st,now); return false; }
            }
            Vec3 world = Spaces.world(s,next);
            m.getMoveControl().setWantedPosition(world.x,world.y,world.z,speed);
            return true;
        }
        if (now < st.sampleAt+20) return false;
        boolean stalled = here.subtract(st.sample).horizontalDistanceSqr()<.04;
        st.sample=here; st.sampleAt=now;
        if (here.distanceToSqr(st.progress)>1) { st.progress=here; st.failures=0; }
        if (!stalled || now<st.retryAt) return false;
        // Follow the existing next node (or one nearby node ahead), in the same frame.
        int index=path.getNextNodeIndex();
        Vec3 goal=node(path,index);
        if (index+1<path.getNodeCount() && goal.distanceToSqr(here)<.36) goal=node(path,++index);
        st.route=plan(m,goal);
        // A foreign structure may occupy the next grid node itself. Try one node
        // beyond it, still within the same short range and with every segment checked.
        if(st.route.isEmpty() && index+1<path.getNodeCount()) st.route=plan(m,node(path,++index));
        st.retryAt=now+100;
        if (st.route.isEmpty()) { failed(m,st,now); return false; }
        st.routeIndex=0; st.resumeIndex=index; st.expires=now+60; st.checkAt=now;
        Vec3 world=Spaces.world(s,st.route.getFirst());
        m.getMoveControl().setWantedPosition(world.x,world.y,world.z,speed);
        return true;
    }
    private static Vec3 node(Path path,int index) {
        var n=path.getNode(index); // Raw nodes are local; Sable projects getNodePos/getNextNodePos.
        return new Vec3(n.x+.5,n.y,n.z+.5);
    }
    private static void failed(EntityMaid m,State st,long now) {
        st.route=List.of(); st.retryAt=now+100;
        // Recomputing the same grid route cannot solve an indefinitely blocked passage.
        if (st.failures<2) { st.failures++; m.getNavigation().recomputePath(); }
    }

    /** At most ten three-segment alternatives within 2.5 blocks, only on supported ground. */
    public static List<Vec3> plan(EntityMaid m,Vec3 goal) {
        SubLevel s=Spaces.tracking(m);
        if (!Spaces.upright(s) || Work.active(m) && !Work.ready(m)) return List.of();
        Vec3 start=Spaces.local(s,m.position()), delta=goal.subtract(start).multiply(1,0,1);
        double distance=delta.length();
        if (distance<.2 || distance>2.5 || Math.abs(goal.y-start.y)>.6) return List.of();
        Corridor c=new Corridor(m,s);
        Vec3 end=c.stand(goal);
        if (end==null) return List.of();
        Vec3 forward=delta.scale(1/distance), side=new Vec3(-forward.z,0,forward.x);
        for (double back:new double[]{0,.4}) for (double offset:new double[]{.35,-.35,.7,-.7,1.1,-1.1}) {
            if (back>0 && Math.abs(offset)<.5) continue;
            Vec3 a=c.stand(start.add(side.scale(offset)).subtract(forward.scale(back)));
            Vec3 b=c.stand(end.add(side.scale(offset)));
            if (a!=null && b!=null && c.segment(start,a) && c.segment(a,b) && c.segment(b,end))
                return List.of(a,b,end);
        }
        return List.of();
    }

    /** A per-attempt snapshot: spatial broad phase, then real voxel shapes, with fixed work limits. */
    private static final class Corridor {
        final EntityMaid maid;
        final SubLevel frame;
        final boolean commuting;
        final List<Obstacles> obstacles=new ArrayList<>();
        final Map<BlockPos,Boolean> allowed=new HashMap<>();
        final Map<BlockPos,List<AABB>> supportShapes=new HashMap<>();
        boolean valid=true;
        int shapeCount;
        Corridor(EntityMaid m,SubLevel s) {
            maid=m; frame=s;
            // The existing schedule may intentionally cross the gap between compass areas.
            // Permit a short deviation during that commute, without extending any work search area.
            var walk=m.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            commuting=m.isHomeModeEnable() && !m.isWithinRestriction(Spaces.localPosition(m))
                    && walk.isPresent() && m.isWithinRestriction(walk.get().getTarget().currentBlockPosition())
                    && Spaces.belongs(m.level(),walk.get().getTarget().currentBlockPosition(),s);
            AABB region=m.getBoundingBox().inflate(4,1,4);
            List<AABB> world=new ArrayList<>();
            for (var shape:m.level().getBlockCollisions(m,region)) {
                for (AABB box:shape.toAabbs()) {
                    if (++shapeCount>256) { valid=false; return; }
                    world.add(box);
                }
            }
            obstacles.add(new Obstacles(null,world));
            Set<SubLevel> nearby=new HashSet<>();
            if(s!=null) nearby.add(s); // Its current pose may be newer than broad-phase bounds.
            for(var other:Sable.HELPER.getAllIntersecting(m.level(),new BoundingBox3d(region))) {
                if(!other.isRemoved()) nearby.add(other);
                if(nearby.size()>16) { valid=false; return; }
            }
            for(var other:nearby) {
                // This small ground recovery handles rigid transforms, not resized worlds.
                if(other.logicalPose().scale().distanceSquared(1,1,1)>1e-8) { valid=false; return; }
                AABB local=localBox(other,region);
                List<AABB> boxes=new ArrayList<>();
                for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(local.minX,local.minY-1,local.minZ),
                        BlockPos.containing(local.maxX,local.maxY,local.maxZ))) {
                    if(!Spaces.belongs(m.level(),p,other) || !m.level().hasChunkAt(p)) continue;
                    for(AABB box:m.level().getBlockState(p).getCollisionShape(m.level(),p).toAabbs()) {
                        if(++shapeCount>256) { valid=false; return; }
                        boxes.add(box.move(p));
                    }
                }
                obstacles.add(new Obstacles(other,boxes));
            }
        }
        Vec3 stand(Vec3 point) {
            double floor=support(point);
            if(!Double.isFinite(floor)) return null;
            // Entities remain vertical in world space, including on mildly tilted decks.
            Vec3 up=direction(frame,AXES[1]);
            double lift=(Math.abs(direction(frame,AXES[0]).y)+Math.abs(direction(frame,AXES[2]).y))
                    *maid.getBbWidth()/2/Math.max(.9,up.y)+.025;
            return Spaces.local(frame,Spaces.world(frame,new Vec3(point.x,floor,point.z)).add(0,lift,0));
        }
        double support(Vec3 point) {
            BlockPos cell=BlockPos.containing(point.add(0,1e-4,0));
            if(!allowed.computeIfAbsent(cell,p -> Spaces.belongs(maid.level(),p,frame)
                    && (commuting || Work.blockAllowed(maid,p) && (!maid.isHomeModeEnable() || maid.isWithinRestriction(p))))) return Double.NaN;
            double best=Double.NEGATIVE_INFINITY;
            for(int dy=-1;dy<=0;dy++) {
                BlockPos p=cell.offset(0,dy,0);
                if(!maid.level().hasChunkAt(p) || !Spaces.belongs(maid.level(),p,frame)) continue;
                var shapes=supportShapes.computeIfAbsent(p,q -> {
                    var state=maid.level().getBlockState(q);
                    if(state.is(TagBlock.MAID_AVOID_BLOCK) || !state.getFluidState().isEmpty()
                            || !maid.level().getFluidState(q.above()).isEmpty()) return List.of();
                    return state.getCollisionShape(maid.level(),q).toAabbs().stream().map(box -> box.move(q)).toList();
                });
                for(AABB box:shapes) {
                    if(point.x>=box.minX && point.x<=box.maxX && point.z>=box.minZ && point.z<=box.maxZ
                            && box.maxY<=point.y+.4 && box.maxY>=point.y-.55) best=Math.max(best,box.maxY);
                }
            }
            return best;
        }
        boolean segment(Vec3 from,Vec3 to) {
            if(!valid || from.distanceToSqr(to)>16) return false;
            Vec3 a=Spaces.world(frame,from),b=Spaces.world(frame,to);
            AABB body=maid.getBoundingBox().move(a.subtract(maid.position())).deflate(.002);
            AABB end=body.move(b.subtract(a));
            if(!maid.level().getWorldBorder().isWithinBounds(body.minmax(end))) return false;
            // Sample support only; collision uses a continuous sweep, so thin walls cannot be skipped.
            int samples=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.2));
            double r=maid.getBbWidth()*.4;
            for(int i=0;i<=samples;i++) {
                Vec3 p=from.lerp(to,(double)i/samples);
                double floor=support(p);
                if(!Double.isFinite(floor)) return false;
                for(Vec3 offset:List.of(new Vec3(r,0,r),new Vec3(r,0,-r),new Vec3(-r,0,r),new Vec3(-r,0,-r))) {
                    double edge=support(p.add(offset));
                    if(!Double.isFinite(edge) || Math.abs(edge-floor)>.2) return false;
                }
            }
            for(Obstacles group:obstacles) if(group.blocks(body,b.subtract(a))) return false;
            return true;
        }
    }

    private static Vec3 direction(SubLevel frame,Vec3 vector) {
        if(frame==null) return vector;
        var q=frame.logicalPose().orientation();
        var v=q.transformInverse(new org.joml.Vector3d(vector.x,vector.y,vector.z));
        return new Vec3(v.x,v.y,v.z);
    }
    private static AABB localBox(SubLevel frame,AABB box) {
        Vec3 min=new Vec3(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        Vec3 max=new Vec3(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY);
        for(int i=0;i<8;i++) {
            Vec3 p=Spaces.local(frame,new Vec3((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ));
            min=new Vec3(Math.min(min.x,p.x),Math.min(min.y,p.y),Math.min(min.z,p.z));
            max=new Vec3(Math.max(max.x,p.x),Math.max(max.y,p.y),Math.max(max.z,p.z));
        }
        return new AABB(min,max);
    }
    private record Obstacles(SubLevel frame,List<AABB> boxes) {
        boolean blocks(AABB body,Vec3 movement) {
            Vec3 center=Spaces.local(frame,body.getCenter()),velocity=direction(frame,movement);
            Vec3[] basis={direction(frame,AXES[0]),direction(frame,AXES[1]),direction(frame,AXES[2])};
            double[] half={body.getXsize()/2,body.getYsize()/2,body.getZsize()/2};
            List<Vec3> axes=new ArrayList<>(15);
            Collections.addAll(axes,AXES); Collections.addAll(axes,basis);
            for(Vec3 a:AXES) for(Vec3 b:basis) { Vec3 cross=a.cross(b); if(cross.lengthSqr()>1e-10) axes.add(cross.normalize()); }
            for(AABB box:boxes) {
                double enter=0,exit=1;
                Vec3 difference=center.subtract(box.getCenter());
                for(Vec3 axis:axes) {
                    double extent=Math.abs(axis.x)*box.getXsize()/2+Math.abs(axis.y)*box.getYsize()/2+Math.abs(axis.z)*box.getZsize()/2;
                    for(int j=0;j<3;j++) extent+=Math.abs(axis.dot(basis[j]))*half[j];
                    double start=axis.dot(difference),speed=axis.dot(velocity);
                    if(Math.abs(speed)<1e-9) { if(Math.abs(start)>=extent) { enter=2; break; } }
                    else {
                        double t1=(-extent-start)/speed,t2=(extent-start)/speed;
                        enter=Math.max(enter,Math.min(t1,t2)); exit=Math.min(exit,Math.max(t1,t2));
                        if(enter>exit) break;
                    }
                }
                if(enter<=exit) return true;
            }
            return false;
        }
    }
}
