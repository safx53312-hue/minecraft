package dev.enchantpoints.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the HUD know where the inventory panel really is (it shifts when the recipe book opens). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x") int enchantpoints$getX();
    @Accessor("y") int enchantpoints$getY();
    @Accessor("backgroundWidth") int enchantpoints$getBackgroundWidth();
    @Accessor("backgroundHeight") int enchantpoints$getBackgroundHeight();
}
