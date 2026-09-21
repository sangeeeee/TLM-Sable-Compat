package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value=EntityMaid.class,remap=false)
public class PickupMixin {
    @Redirect(method="pushEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> nearby(Level level,Entity self,AABB box,Predicate<Entity> predicate) {
        EntityMaid m=(EntityMaid)(Object)this;
        return level.getEntities(self,Pickup.queryBox(m,box),e->
                (box.intersects(e.getBoundingBox()) || e instanceof ItemEntity && Pickup.near(m,e,AddonWork.position(m))) && predicate.test(e));
    }
    @Inject(method="pickupItem",at=@At("HEAD"),cancellable=true)
    private void allowed(ItemEntity item,boolean simulate,CallbackInfoReturnable<Boolean> cir) {
        EntityMaid m=(EntityMaid)(Object)this;
        if (!Pickup.allowed(m,item) || !simulate && !Pickup.visible(m,item,AddonWork.position(m))) cir.setReturnValue(false);
    }
}
