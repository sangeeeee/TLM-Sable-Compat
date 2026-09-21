package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import com.sange.tlm_sablecompat.*;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;
import java.util.UUID;

@Mixin(value=MaidFishingHook.class,remap=false)
public abstract class FishingHookMixin implements Fishing.Anchor {
    @Unique private static final EntityDataAccessor<Optional<UUID>> STRUCTURE=SynchedEntityData.defineId(MaidFishingHook.class,EntityDataSerializers.OPTIONAL_UUID);
    @Unique private static final EntityDataAccessor<BlockPos> WATER=SynchedEntityData.defineId(MaidFishingHook.class,EntityDataSerializers.BLOCK_POS);
    @Unique private double compat$height=0.5;
    @Unique private double compat$velocity;
    @Shadow protected abstract void waterSurfaceTick();
    @Shadow protected abstract void bobbingTick(BlockPos pos,float height);
    @Shadow protected abstract void checkOpenWater(BlockPos pos);
    @Shadow protected abstract void bitingTick(BlockPos pos);

    @Inject(method="defineSynchedData",at=@At("TAIL"))
    private void data(SynchedEntityData.Builder builder,CallbackInfo ci) {
        builder.define(STRUCTURE,Optional.empty()).define(WATER,BlockPos.ZERO);
    }
    public void compat$water(UUID structure,BlockPos pos) {
        var hook=(MaidFishingHook)(Object)this;
        hook.getEntityData().set(STRUCTURE,Optional.of(structure));hook.getEntityData().set(WATER,pos);
    }
    // Resolve moved anchors before the original owner-distance check can discard a moving hook.
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void followWater(CallbackInfo ci) {
        var hook=(MaidFishingHook)(Object)this;
        if (hook.getEntityData().get(STRUCTURE).isEmpty()) return;
        if (hook.level() instanceof ServerLevel level) {
            var tag=hook.getPersistentData();
            Binding b=tag.hasUUID(Resting.KEY)?BindingStore.get(level).find(tag.getUUID(Resting.KEY)):null;
            if (b==null || !b.failure.isEmpty() || b.points.isEmpty() || b.points.getFirst().structure()==null) {
                hook.discard();ci.cancel();return;
            }
            var p=b.points.getFirst();compat$water(p.structure(),p.position());
        }
        var s=Spaces.resolve(hook.level(),hook.getEntityData().get(STRUCTURE).orElse(null));
        BlockPos pos=hook.getEntityData().get(WATER);
        var maid=hook.getMaidOwner();
        if (s==null || !Spaces.upright(s) || !hook.level().hasChunkAt(pos)
                || !Spaces.belongs(hook.level(),pos,s) || !hook.level().getFluidState(pos).is(FluidTags.WATER)
                || !hook.level().isClientSide() && (maid==null || !Work.blockAllowed(maid,pos))) {
            if (!hook.level().isClientSide()) hook.discard();ci.cancel();return;
        }
        hook.setPos(Spaces.world(s,Vec3.atLowerCornerOf(pos).add(0.5,compat$height,0.5)));
        ((EntityMovementExtension)hook).sable$setTrackingSubLevel(s);
    }
    @Redirect(method="bobbingTick",at=@At(value="INVOKE",target="Lcom/github/tartaricacid/touhoulittlemaid/entity/projectile/MaidFishingHook;getY()D"))
    private double localHeight(MaidFishingHook hook) {
        return hook.getEntityData().get(STRUCTURE).isPresent()?hook.getEntityData().get(WATER).getY()+compat$height:hook.getY();
    }
    @Inject(method="fishingTick",at=@At("HEAD"),cancellable=true)
    private void localWaterTick(CallbackInfo ci) {
        var hook=(MaidFishingHook)(Object)this;
        if (hook.getEntityData().get(STRUCTURE).isEmpty()) return;
        ci.cancel();
        var s=Spaces.resolve(hook.level(),hook.getEntityData().get(STRUCTURE).orElse(null));
        if (s==null) return;
        BlockPos pos=hook.getEntityData().get(WATER);
        var fluid=hook.level().getFluidState(pos);
        if (!fluid.is(FluidTags.WATER)) return;
        // Keep the original bite timers, open-water test, loot and rod wear. Only buoyancy
        // is evaluated along the pool's local Y; entity position always remains world-space.
        waterSurfaceTick();
        hook.setDeltaMovement(0,compat$velocity,0);
        bobbingTick(pos,fluid.getHeight(hook.level(),pos));
        checkOpenWater(pos);bitingTick(pos);
        if (hook.isRemoved()) return;
        compat$velocity=hook.getDeltaMovement().y*0.92;
        compat$height=Math.max(0.05,Math.min(0.99,compat$height+hook.getDeltaMovement().y));
        hook.setDeltaMovement(Vec3.ZERO);
        hook.setPos(Spaces.world(s,Vec3.atLowerCornerOf(pos).add(0.5,compat$height,0.5)));
    }
}
