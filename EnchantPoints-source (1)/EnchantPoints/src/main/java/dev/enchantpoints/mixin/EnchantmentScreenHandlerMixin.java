package dev.enchantpoints.mixin;

import dev.enchantpoints.ConfigManager;
import dev.enchantpoints.PointsManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enchanting table guard. The table adds the "clue" enchantment (plus possibly extra random ones that are only rolled
 * inside the click handler). We can reliably price the guaranteed clue enchantment; if even that does not fit, the
 * click is refused. Any extra random enchantments that push the item over are caught afterwards by the normal
 * recalculation (the new item is disabled, never deleted). See README "Known limitations".
 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
    @Shadow @Final private Inventory inventory;
    @Shadow @Final public int[] enchantmentId;
    @Shadow @Final public int[] enchantmentLevel;

    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void enchantpoints$guard(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayerEntity sp) || id < 0 || id >= enchantmentId.length) return;
        ItemStack item = inventory.getStack(0);
        if (item.isEmpty()) return;
        var registry = sp.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var clue = registry.getEntry(enchantmentId[id]);
        if (clue.isEmpty()) return;
        int clueCost = PointsManager.enchantCost(ConfigManager.get(), clue.get(), enchantmentLevel[id]);
        if (clueCost <= 0) return;

        int base = PointsManager.computeTotal(sp);
        int itemCost = PointsManager.stackCost(item);
        int before = base + itemCost;
        int after = before + clueCost;
        if (after > ConfigManager.get().maxPoints) {
            PointsManager.notifyDenied(sp, before, after);
            cir.setReturnValue(false);
        }
    }
}
