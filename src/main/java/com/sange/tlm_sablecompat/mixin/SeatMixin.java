package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.sange.tlm_sablecompat.Resting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=EntitySit.class,remap=false)
public class SeatMixin implements Resting.SeatData {
    @Shadow private BlockPos associatedBlockPos;
    @Unique private static final EntityDataAccessor<BlockPos> ANCHOR=SynchedEntityData.defineId(EntitySit.class,EntityDataSerializers.BLOCK_POS);
    @Unique private static final EntityDataAccessor<Vector3f> OFFSET=SynchedEntityData.defineId(EntitySit.class,EntityDataSerializers.VECTOR3);
    @Unique private static final EntityDataAccessor<Float> YAW=SynchedEntityData.defineId(EntitySit.class,EntityDataSerializers.FLOAT);
    @Inject(method="defineSynchedData",at=@At("TAIL"))
    private void data(SynchedEntityData.Builder builder,CallbackInfo ci) { builder.define(ANCHOR,BlockPos.ZERO).define(OFFSET,new Vector3f()).define(YAW,0f); }
    @Inject(method="tick",at=@At("HEAD"))
    private void move(CallbackInfo ci) { Resting.seat((EntitySit)(Object)this); }
    public BlockPos compat$anchor() { return ((EntitySit)(Object)this).getEntityData().get(ANCHOR); }
    public Vector3f compat$offset() { return ((EntitySit)(Object)this).getEntityData().get(OFFSET); }
    public float compat$yaw() { return ((EntitySit)(Object)this).getEntityData().get(YAW); }
    public void compat$set(BlockPos pos,Vector3f offset,float yaw) {
        var data=((EntitySit)(Object)this).getEntityData(); data.set(ANCHOR,pos); data.set(OFFSET,offset); data.set(YAW,yaw);
    }
    public void compat$associated(BlockPos pos) { associatedBlockPos=pos; }
}
