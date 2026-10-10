package dev.enchantpoints.mixin;

import dev.enchantpoints.PointsManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fallback for the "inventory is completely full" case: a worn armor piece that is disabled but could not be
 * moved out is reported as EMPTY to the game's equipment logic (armor value, toughness, enchantment effects).
 * The real stack is untouched in the inventory, so nothing is ever deleted.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getEquippedStack", at = @At("RETURN"), cancellable = true)
    private void enchantpoints$hideSuppressedArmor(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (PointsManager.suppressedEmpty() || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        if (!((Object) this instanceof ServerPlayerEntity)) return;
        if (PointsManager.isSuppressed(cir.getReturnValue())) cir.setReturnValue(ItemStack.EMPTY);
    }
}
