package dev.enchantpoints;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.enchantpoints.core.PointsConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PointsCommands {
    private PointsCommands() {}

    /**
     * Operator (gamemaster level) OR the permission node "enchantpoints.admin" if the optional
     * fabric-permissions-api (LuckPerms etc.) is installed. Looked up reflectively so it stays optional.
     *
     * NOTE: 1.21.9+ reworked the vanilla permission API. If this line does not compile for you, replace
     * it with whatever the current "level 2 / gamemaster" check is (older versions: src.hasPermissionLevel(2)).
     */
    private static final Predicate<ServerCommandSource> VANILLA_OP =
            CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK);

    static boolean isAdmin(ServerCommandSource src) {
        if (VANILLA_OP.test(src)) return true;
        try {
            Class<?> c = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method m = c.getMethod("check", net.minecraft.command.CommandSource.class, String.class);
            return (boolean) m.invoke(null, src, "enchantpoints.admin");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerAdmin(dispatcher);
            registerInfo(dispatcher);
        });
    }

    private static void registerAdmin(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("setenchantpoint")
                .requires(PointsCommands::isAdmin)
                .then(argument("value", IntegerArgumentType.integer(0, 1_000_000)).executes(ctx -> {
                    int v = IntegerArgumentType.getInteger(ctx, "value");
                    ConfigManager.get().maxPoints = v;
                    if (!persist(ctx)) return 0;
                    PointsManager.recalcAll(ctx.getSource().getServer());   // immediate effect for everyone online
                    ok(ctx, "Enchant Point maximum set to " + v + ".");
                    return v;
                }))
                .then(literal("get").executes(ctx -> {
                    int v = ConfigManager.get().maxPoints;
                    ok(ctx, "Enchant Point maximum is " + v + ".");
                    return v;
                }))
                .then(literal("reload").executes(ctx -> {
                    List<String> errs = ConfigManager.load();
                    if (!errs.isEmpty()) {
                        errs.forEach(e -> ctx.getSource().sendError(Text.literal("Config error: " + e)));
                        ctx.getSource().sendError(Text.literal("Reload failed; the previous valid configuration is still active."));
                        return 0;
                    }
                    PointsManager.recalcAll(ctx.getSource().getServer());
                    ok(ctx, "EnchantPoints configuration reloaded (max " + ConfigManager.get().maxPoints + ").");
                    return 1;
                }))
                .then(literal("cost")
                        .then(argument("enchantment", IdentifierArgumentType.identifier())
                                .then(argument("points_per_level", IntegerArgumentType.integer(0, 10_000)).executes(ctx -> {
                                    Identifier id = IdentifierArgumentType.getIdentifier(ctx, "enchantment");
                                    int cost = IntegerArgumentType.getInteger(ctx, "points_per_level");
                                    PointsConfig cfg = ConfigManager.get();
                                    String key = id.toString();
                                    if (cfg.fixedCosts.containsKey(key) || cfg.tieredCosts.containsKey(key)) {
                                        ctx.getSource().sendError(Text.literal(key + " uses a fixed or tiered rule. Edit config/enchantpoints.json and run /setenchantpoint reload."));
                                        return 0;
                                    }
                                    cfg.perLevelCosts.put(key, cost);
                                    if (!persist(ctx)) return 0;
                                    PointsManager.recalcAll(ctx.getSource().getServer());
                                    ok(ctx, key + " now costs " + cost + " point(s) per level.");
                                    return cost;
                                })))));
    }

    private static void registerInfo(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("enchantpoints")
                .executes(ctx -> show(ctx, ctx.getSource().getPlayerOrThrow()))
                .then(argument("player", EntityArgumentType.player())
                        .requires(PointsCommands::isAdmin)
                        .executes(ctx -> show(ctx, EntityArgumentType.getPlayer(ctx, "player")))));
    }

    private static int show(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) throws CommandSyntaxException {
        int total = PointsManager.computeTotal(target);
        int max = ConfigManager.get().maxPoints;
        boolean self = ctx.getSource().getPlayer() == target;
        String who = self ? "" : target.getName().getString() + " - ";
        ctx.getSource().sendFeedback(() -> Text.literal(who).append(PointsManager.statusText(total, max)), false);
        if (total > max) {
            ctx.getSource().sendFeedback(() -> Text.literal("Over limit by: " + (total - max)).formatted(Formatting.RED), false);
        } else {
            ctx.getSource().sendFeedback(() -> Text.literal("Remaining: " + (max - total)).formatted(Formatting.GRAY), false);
        }
        return total;
    }

    private static boolean persist(CommandContext<ServerCommandSource> ctx) {
        try { ConfigManager.save(); return true; }
        catch (IOException e) {
            ctx.getSource().sendError(Text.literal("Could not save config: " + e.getMessage()));
            EnchantPointsMod.LOG.error("[EnchantPoints] save failed", e);
            return false;
        }
    }

    private static void ok(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(() -> Text.literal(msg).formatted(Formatting.GREEN), true);
    }
}
