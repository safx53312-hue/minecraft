package dev.enchantpoints.core;

import java.util.*;
import java.util.regex.Pattern;

/** Plain data model of config/enchantpoints.json. No Minecraft dependencies. */
public final class PointsConfig {
    private static final Pattern ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    public int maxPoints = 20;
    public int defaultCostPerLevel = 1;
    /** Safety poll interval; event-driven recalculation happens in addition to this. */
    public int safetyCheckIntervalTicks = 5;

    /** Charged ONCE per enchantment on an item, whatever its level. */
    public Map<String, Integer> fixedCosts = new LinkedHashMap<>();
    /** Linear: level * cost. */
    public Map<String, Integer> perLevelCosts = new LinkedHashMap<>();
    /** Each level costs the price of the tier it falls in. Levels outside every tier use defaultCostPerLevel. */
    public Map<String, List<Tier>> tieredCosts = new LinkedHashMap<>();

    public Disabling disabling = new Disabling();
    public Display display = new Display();
    public Messages messages = new Messages();

    public static final class Tier {
        public int minLevel;
        public int maxLevel;
        public int costPerLevel;
        public Tier() {}
        public Tier(int min, int max, int cost) { minLevel = min; maxLevel = max; costPerLevel = cost; }
    }

    public static final class Disabling {
        public boolean armor = true;
        public boolean weapons = true;
        public boolean tools = true;
        public boolean rangedWeapons = true;
    }

    public static final class Display {
        public boolean inventoryHud = true;
        public boolean actionBar = false;
        /** HUD turns yellow at or above this percentage of the maximum. */
        public int yellowAtPercent = 75;
    }

    public static final class Messages {
        public String overLimit = "You are over your Enchant Point limit (%total%/%max%). %disabled% item(s) disabled until you are at or under the limit.";
        public String backUnderLimit = "You are back within your Enchant Point limit (%total%/%max%). Your items work again.";
        public String disabledItem = "That item is disabled (Enchant Points over limit).";
        public String operationDenied = "That operation would exceed your Enchant Point limit.\nCurrent: %current%/%max%\nAfter operation: %after%/%max%";
        public String armorMoved = "A disabled armor piece was moved to your inventory.";
    }

    public static PointsConfig defaults() {
        PointsConfig c = new PointsConfig();
        c.fixedCosts.put("minecraft:mending", 2);
        c.perLevelCosts.put("minecraft:lunge", 2);
        c.perLevelCosts.put("minecraft:breach", 4);
        c.tieredCosts.put("minecraft:protection", new ArrayList<>(List.of(new Tier(1, 5, 1), new Tier(6, 999, 2))));
        c.tieredCosts.put("minecraft:sharpness", new ArrayList<>(List.of(new Tier(1, 3, 1), new Tier(4, 7, 2), new Tier(8, 999, 3))));
        return c;
    }

    /** @return human readable errors; empty list means valid. Never silently repairs anything. */
    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (maxPoints < 0) e.add("maxPoints must be >= 0 (was " + maxPoints + ")");
        if (defaultCostPerLevel < 0) e.add("defaultCostPerLevel must be >= 0");
        if (safetyCheckIntervalTicks < 1 || safetyCheckIntervalTicks > 200) e.add("safetyCheckIntervalTicks must be 1..200");
        if (fixedCosts == null || perLevelCosts == null || tieredCosts == null) {
            e.add("fixedCosts, perLevelCosts and tieredCosts must all be present (use {} for none)");
            return e;
        }
        if (disabling == null) e.add("'disabling' section missing");
        if (display == null) e.add("'display' section missing");
        else if (display.yellowAtPercent < 1 || display.yellowAtPercent > 100) e.add("display.yellowAtPercent must be 1..100");
        if (messages == null) e.add("'messages' section missing");

        Set<String> seen = new HashSet<>();
        checkIds("fixedCosts", fixedCosts.keySet(), seen, e);
        checkIds("perLevelCosts", perLevelCosts.keySet(), seen, e);
        checkIds("tieredCosts", tieredCosts.keySet(), seen, e);
        fixedCosts.forEach((k, v) -> { if (v == null || v < 0) e.add("fixedCosts." + k + " must be >= 0"); });
        perLevelCosts.forEach((k, v) -> { if (v == null || v < 0) e.add("perLevelCosts." + k + " must be >= 0"); });
        tieredCosts.forEach((k, tiers) -> {
            if (tiers == null || tiers.isEmpty()) { e.add("tieredCosts." + k + " has no tiers"); return; }
            List<Tier> sorted = new ArrayList<>(tiers);
            sorted.sort(Comparator.comparingInt(t -> t.minLevel));
            Tier prev = null;
            for (Tier t : sorted) {
                if (t.minLevel < 1) e.add("tieredCosts." + k + ": minLevel must be >= 1");
                if (t.maxLevel < t.minLevel) e.add("tieredCosts." + k + ": maxLevel " + t.maxLevel + " < minLevel " + t.minLevel);
                if (t.costPerLevel < 0) e.add("tieredCosts." + k + ": costPerLevel must be >= 0");
                if (prev != null && t.minLevel <= prev.maxLevel)
                    e.add("tieredCosts." + k + ": tiers overlap (" + prev.minLevel + "-" + prev.maxLevel + " and " + t.minLevel + "-" + t.maxLevel + ")");
                prev = t;
            }
        });
        return e;
    }

    private static void checkIds(String section, Set<String> ids, Set<String> seen, List<String> e) {
        for (String id : ids) {
            if (!ID.matcher(id).matches()) e.add(section + ": '" + id + "' is not a valid identifier (expected namespace:path)");
            if (!seen.add(id)) e.add("'" + id + "' is defined in more than one cost section; it would be ambiguous");
        }
    }
}
