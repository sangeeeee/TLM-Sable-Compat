package com.sange.tlm_sablecompat.mixin;

import com.github.tartaricacid.touhoulittlemaid.item.ItemKappaCompass;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tlm_sablecompat.Compasses;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemKappaCompass.class, remap = false)
public class CompassMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void record(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) { cir.setReturnValue(Compasses.record(ctx)); }
    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
    private void bind(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (entity instanceof EntityMaid maid) cir.setReturnValue(Compasses.bind(stack, player, maid));
    }
}
