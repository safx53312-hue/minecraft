package dev.enchantpoints.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ForgingScreenHandler.class)
public interface ForgingScreenHandlerAccessor {
    @Accessor("player") PlayerEntity enchantpoints$getPlayer();
    @Accessor("output") CraftingResultInventory enchantpoints$getOutput();
    @Accessor("input") Inventory enchantpoints$getInput();
}
