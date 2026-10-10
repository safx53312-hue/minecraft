package dev.enchantpoints;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

/** All enforcement hooks. Everything here runs on the logical server only. */
public final class PointsEvents {
    private PointsEvents() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PointsManager::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PointsManager.recalc(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PointsManager.forget(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> PointsManager.markDirty(newP));
        ServerPlayerEvents.COPY_FROM.register((oldP, newP, alive) -> PointsManager.markDirty(newP));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> PointsManager.markDirty(player));

        // Test D: a disabled weapon deals no damage. Covers melee, sweep, mace smash, crits (all are one damage call),
        // and projectiles (DamageSource.getWeaponStack() is the bow/crossbow/trident that fired them).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) return true;
            ItemStack weapon = source.getWeaponStack();
            if (weapon == null || weapon.isEmpty()) weapon = attacker.getMainHandStack();
            if (PointsManager.blocksFunction(weapon)) {
                PointsManager.warnDisabled(attacker);
                return false;
            }
            return true;
        });

        // Disabled tools do not break blocks
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity sp && PointsManager.blocksFunction(sp.getMainHandStack())) {
                PointsManager.warnDisabled(sp);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (player instanceof ServerPlayerEntity sp && PointsManager.blocksFunction(sp.getMainHandStack())) {
                PointsManager.warnDisabled(sp);
                return false;
            }
            return true;
        });

        // Disabled hoes/shovels/axes can't strip/till/path; disabled bows/crossbows/tridents can't start being used;
        // disabled armor can't be equipped by right-click.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity sp && PointsManager.blocksFunction(sp.getStackInHand(hand))) {
                PointsManager.warnDisabled(sp);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack held = sp.getStackInHand(hand);
            if (PointsManager.blocksFunction(held) || PointsManager.blocksEquip(sp, held)) {
                PointsManager.warnDisabled(sp);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}
