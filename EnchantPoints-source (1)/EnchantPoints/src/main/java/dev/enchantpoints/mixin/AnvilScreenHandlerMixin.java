package dev.enchantpoints.mixin;

import dev.enchantpoints.ConfigManager;
import dev.enchantpoints.PointsManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test I. After vanilla computes the anvil result, simulate the final inventory:
 *   before = carried total + both anvil inputs        (they are out of the inventory while in the anvil)
 *   after  = carried total + the result               (the inputs are consumed, so they are NOT counted twice)
 * If that would push the player over the limit AND increases their points, the result slot is emptied.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Shadow @Final private Property levelCost;

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void enchantpoints$guard(CallbackInfo ci) {
        ForgingScreenHandlerAccessor acc = (ForgingScreenHandlerAccessor) (Object) this;
        if (!(acc.enchantpoints$getPlayer() instanceof ServerPlayerEntity sp)) return;
        ItemStack out = acc.enchantpoints$getOutput().getStack(0);
        if (out.isEmpty()) return;

        int base = PointsManager.computeTotal(sp);
        int inputs = PointsManager.stackCost(acc.enchantpoints$getInput().getStack(0))
                   + PointsManager.stackCost(acc.enchantpoints$getInput().getStack(1));
        int before = base + inputs;
        int after = base + PointsManager.stackCost(out);
        if (after > ConfigManager.get().maxPoints && after > before) {
            acc.enchantpoints$getOutput().setStack(0, ItemStack.EMPTY);
            this.levelCost.set(0);
            PointsManager.notifyDenied(sp, before, after);
        }
    }
}
