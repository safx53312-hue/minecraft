package dev.enchantpoints.core;

import java.util.List;

/** The one central cost function. Pure math on (enchantment id, level). */
public final class CostRules {
    private CostRules() {}

    public static int enchantCost(PointsConfig c, String id, int level) {
        if (level <= 0) return 0;

        Integer fixed = c.fixedCosts.get(id);
        if (fixed != null) return fixed;                       // e.g. Mending: once, any level

        List<PointsConfig.Tier> tiers = c.tieredCosts.get(id);
        if (tiers != null) {
            long total = 0;
            long covered = 0;
            for (PointsConfig.Tier t : tiers) {
                long upper = Math.min(level, t.maxLevel);
                long count = upper - t.minLevel + 1;           // levels of this tier that are reached
                if (count > 0) { total += count * t.costPerLevel; covered += count; }
            }
            total += (level - covered) * (long) c.defaultCostPerLevel;   // levels not in any tier
            return clamp(total);
        }

        Integer per = c.perLevelCosts.get(id);
        return clamp((long) level * (per != null ? per : c.defaultCostPerLevel));
    }

    private static int clamp(long v) { return (int) Math.min(v, Integer.MAX_VALUE / 2); }
}
