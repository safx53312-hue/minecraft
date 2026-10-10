package dev.enchantpoints;

import dev.enchantpoints.core.CostRules;
import dev.enchantpoints.core.DisableSelector;
import dev.enchantpoints.core.PointsConfig;
import dev.enchantpoints.net.PointsSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative shared enchant-point pool.
 *
 * Identity: every enchanted stack gets a hidden long id in minecraft:custom_data ("enchantpoints_id").
 * Nothing visible (name/lore/model) and no enchantment is touched. "Disabled" is NOT stored on the item:
 * it lives in DISABLED, which is rebuilt from the CURRENT holder's budget on every recalculation, so a
 * transferred item is judged against its new owner and never stays permanently disabled.
 */
public final class PointsManager {
    private static final String KEY = "enchantpoints_id";
    /** Inventory indices of armor in PlayerInventory: feet, legs, chest, head. */
    private static final int ARMOR_BASE = 36;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    /** ids of stacks that are currently disabled for their holder. */
    private static final Set<Long> DISABLED = ConcurrentHashMap.newKeySet();
    /** subset: disabled armor that is still worn because the inventory was full (hidden from equipment logic). */
    private static final Set<Long> SUPPRESSED = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static long seq = 0;

    private static final class State {
        boolean initialized;
        Map<Long, Long> acquired = new HashMap<>();
        Set<Long> disabled = new HashSet<>();
        int lastTotal = -1, lastMax = -1;
        int lastWarnTick = -1000;
    }

    private PointsManager() {}

    // ---------------------------------------------------------------- cost of stacks

    public static int stackCost(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        PointsConfig cfg = ConfigManager.get();
        int sum = 0;
        sum += componentCost(cfg, stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
        sum += componentCost(cfg, stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
        return sum;
    }

    private static int componentCost(PointsConfig cfg, ItemEnchantmentsComponent comp) {
        if (comp.isEmpty()) return 0;
        int sum = 0;
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : comp.getEnchantmentEntries()) {
            sum += enchantCost(cfg, e.getKey(), e.getIntValue());
        }
        return sum;
    }

    /** Uses the registry id, never the localized name. Level is the real stored level (no vanilla clipping). */
    public static int enchantCost(PointsConfig cfg, RegistryEntry<Enchantment> entry, int level) {
        String id = entry.getKey().map(k -> k.getValue().toString()).orElse("unknown:unknown");
        return CostRules.enchantCost(cfg, id, level);
    }

    /** Pure read: carried inventory (main+hotbar once, armor, offhand) + cursor stack. No side effects. */
    public static int computeTotal(ServerPlayerEntity p) {
        int total = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) total += stackCost(inv.getStack(i));
        total += stackCost(p.currentScreenHandler.getCursorStack());
        return total;
    }

    // ---------------------------------------------------------------- identity

    public static long getId(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? 0L : c.copyNbt().getLong(KEY, 0L);
    }

    private static void stamp(ItemStack stack, long id) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putLong(KEY, id));
    }

    private static long nextSeq() {
        seq = Math.max(seq + 1, System.currentTimeMillis() * 1000L);
        return seq;
    }

    // ---------------------------------------------------------------- queries used by events/mixins

    public static boolean isDisabled(ItemStack s) {
        if (DISABLED.isEmpty() || s.isEmpty()) return false;
        long id = getId(s);
        return id != 0 && DISABLED.contains(id);
    }

    public static boolean isSuppressed(ItemStack s) {
        if (SUPPRESSED.isEmpty() || s.isEmpty()) return false;
        long id = getId(s);
        return id != 0 && SUPPRESSED.contains(id);
    }

    public static boolean suppressedEmpty() { return SUPPRESSED.isEmpty(); }

    /** True if this stack is disabled AND the config says its category is disabled. */
    public static boolean blocksFunction(ItemStack s) {
        if (!isDisabled(s)) return false;
        PointsConfig.Disabling d = ConfigManager.get().disabling;
        if (s.getItem() instanceof RangedWeaponItem) return d.rangedWeapons;
        if (s.contains(DataComponentTypes.WEAPON)) return d.weapons;
        if (s.contains(DataComponentTypes.TOOL)) return d.tools;
        return false;
    }

    /** Stops equipping a disabled armor piece, and stops right-click swapping over a suppressed worn piece. */
    public static boolean blocksEquip(ServerPlayerEntity p, ItemStack held) {
        if (!ConfigManager.get().disabling.armor || held.isEmpty()) return false;
        var eq = held.get(DataComponentTypes.EQUIPPABLE);
        if (eq == null) return false;
        if (isDisabled(held)) return true;
        for (int k = 0; k < 4; k++) {
            if (ARMOR[k] == eq.slot() && isSuppressed(p.getInventory().getStack(ARMOR_BASE + k))) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- scheduling

    public static void markDirty(ServerPlayerEntity p) { DIRTY.add(p.getUuid()); }

    public static void tick(MinecraftServer server) {
        int interval = ConfigManager.get().safetyCheckIntervalTicks;
        boolean poll = server.getTicks() % interval == 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (poll || DIRTY.contains(p.getUuid())) recalc(p);
        }
        DIRTY.clear();
    }

    public static void recalcAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) recalc(p);
    }

    public static void forget(ServerPlayerEntity p) {
        State st = STATES.remove(p.getUuid());
        if (st != null) { DISABLED.removeAll(st.disabled); SUPPRESSED.removeAll(st.disabled); }
    }

    // ---------------------------------------------------------------- the recalculation

    public static void recalc(ServerPlayerEntity p) {
        PointsConfig cfg = ConfigManager.get();
        State st = STATES.computeIfAbsent(p.getUuid(), u -> new State());
        PlayerInventory inv = p.getInventory();

        List<DisableSelector.Candidate> cands = new ArrayList<>();
        Map<Long, Long> newAcquired = new HashMap<>();
        Set<Long> idsSeen = new HashSet<>();
        int[] total = {0};

        int n = inv.size();
        for (int i = 0; i < n; i++) collect(inv.getStack(i), i, st, cands, newAcquired, idsSeen, total);
        collect(p.currentScreenHandler.getCursorStack(), 1000, st, cands, newAcquired, idsSeen, total);

        Set<Long> disabledNow = DisableSelector.select(cands, cfg.maxPoints);

        // publish disabled state: set membership from the CURRENT holder's point of view
        for (DisableSelector.Candidate c : cands) {
            if (disabledNow.contains(c.id())) DISABLED.add(c.id()); else { DISABLED.remove(c.id()); SUPPRESSED.remove(c.id()); }
        }
        for (Long old : st.disabled) if (!idsSeen.contains(old)) { DISABLED.remove(old); SUPPRESSED.remove(old); }

        handleArmor(p, inv, disabledNow, cfg);

        // notifications
        boolean newlyDisabled = false;
        for (Long id : disabledNow) if (!st.disabled.contains(id)) { newlyDisabled = true; break; }
        if (newlyDisabled) {
            send(p, cfg.messages.overLimit, total[0], cfg.maxPoints, disabledNow.size(), Formatting.RED);
        } else if (!st.disabled.isEmpty() && disabledNow.isEmpty()) {
            send(p, cfg.messages.backUnderLimit, total[0], cfg.maxPoints, 0, Formatting.GREEN);
        }

        st.disabled = new HashSet<>(disabledNow);
        st.acquired = newAcquired;
        st.initialized = true;

        if (total[0] != st.lastTotal || cfg.maxPoints != st.lastMax) {
            st.lastTotal = total[0];
            st.lastMax = cfg.maxPoints;
            sync(p, total[0], cfg);
            if (cfg.display.actionBar) p.sendMessage(statusText(total[0], cfg.maxPoints), true);
        }
    }

    private static void collect(ItemStack s, int order, State st, List<DisableSelector.Candidate> cands,
                                Map<Long, Long> newAcquired, Set<Long> idsSeen, int[] total) {
        int cost = stackCost(s);
        if (cost <= 0) return;
        total[0] += cost;
        long id = getId(s);
        if (id == 0 || !idsSeen.add(id)) {          // unstamped, or a duplicate id (cloned stack): give it a fresh identity
            id = nextSeq();
            stamp(s, id);
            idsSeen.add(id);
        }
        Long acq = st.acquired.get(id);
        if (acq == null) acq = st.initialized ? nextSeq() : id;   // newly arrived = newest; on first scan use creation order
        newAcquired.put(id, acq);
        cands.add(new DisableSelector.Candidate(id, cost, acq, order));
    }

    /** Move disabled worn armor into the main inventory; if it is full, hide it from equipment logic instead. */
    private static void handleArmor(ServerPlayerEntity p, PlayerInventory inv, Set<Long> disabledNow, PointsConfig cfg) {
        if (!cfg.disabling.armor) { return; }
        boolean moved = false;
        for (int k = 0; k < 4; k++) {
            ItemStack worn = inv.getStack(ARMOR_BASE + k);
            if (worn.isEmpty()) continue;
            long id = getId(worn);
            if (id == 0 || !disabledNow.contains(id)) { if (id != 0) SUPPRESSED.remove(id); continue; }
            int free = inv.getEmptySlot();
            if (free >= 0) {
                inv.setStack(free, worn);
                inv.setStack(ARMOR_BASE + k, ItemStack.EMPTY);
                SUPPRESSED.remove(id);
                moved = true;
            } else {
                SUPPRESSED.add(id);
            }
        }
        if (moved) p.sendMessage(Text.literal(cfg.messages.armorMoved).formatted(Formatting.YELLOW), false);
    }

    // ---------------------------------------------------------------- messaging / networking

    private static void sync(ServerPlayerEntity p, int total, PointsConfig cfg) {
        if (ServerPlayNetworking.canSend(p, PointsSyncPayload.ID)) {
            ServerPlayNetworking.send(p, new PointsSyncPayload(total, cfg.maxPoints, cfg.display.yellowAtPercent, cfg.display.inventoryHud));
        }
    }

    public static Text statusText(int total, int max) {
        Formatting f = total > max ? Formatting.RED : (total * 100L >= max * 75L ? Formatting.YELLOW : Formatting.GREEN);
        return Text.literal("Enchant Points: " + total + "/" + max).formatted(f);
    }

    private static String fmt(String m, int total, int max, int disabled) {
        return m.replace("%total%", Integer.toString(total)).replace("%max%", Integer.toString(max))
                .replace("%over%", Integer.toString(Math.max(0, total - max))).replace("%disabled%", Integer.toString(disabled));
    }

    private static void send(ServerPlayerEntity p, String msg, int total, int max, int disabled, Formatting color) {
        p.sendMessage(Text.literal(fmt(msg, total, max, disabled)).formatted(color), false);
    }

    /** Throttled "this item is disabled" nudge (actionbar). */
    public static void warnDisabled(ServerPlayerEntity p) {
        State st = STATES.computeIfAbsent(p.getUuid(), u -> new State());
        if (p.age - st.lastWarnTick < 20) return;
        st.lastWarnTick = p.age;
        p.sendMessage(Text.literal(ConfigManager.get().messages.disabledItem).formatted(Formatting.RED), true);
    }

    public static void notifyDenied(ServerPlayerEntity p, int current, int after) {
        State st = STATES.computeIfAbsent(p.getUuid(), u -> new State());
        if (p.age - st.lastWarnTick < 20) return;
        st.lastWarnTick = p.age;
        PointsConfig cfg = ConfigManager.get();
        String m = cfg.messages.operationDenied.replace("%current%", Integer.toString(current))
                .replace("%after%", Integer.toString(after)).replace("%max%", Integer.toString(cfg.maxPoints));
        for (String line : m.split("\n")) p.sendMessage(Text.literal(line).formatted(Formatting.RED), false);
    }
}
