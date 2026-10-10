package dev.enchantpoints.mixin;

import dev.enchantpoints.PointsManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Any slot click (move, shift-click, drag, swap, armor/offhand, cursor) schedules an immediate recalculation. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Inject(method = "onSlotClick", at = @At("TAIL"))
    private void enchantpoints$onClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity sp) PointsManager.markDirty(sp);   // processed at end of tick: no re-entrancy
    }
}
