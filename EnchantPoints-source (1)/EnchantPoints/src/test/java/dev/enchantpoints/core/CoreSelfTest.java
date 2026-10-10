package dev.enchantpoints.core;

import java.util.*;

/** Dependency-free tests (no JUnit needed). Run: see README "Running the core self-test". */
public final class CoreSelfTest {
    static int fails = 0, passes = 0;
    static void eq(String name, Object exp, Object act) {
        if (Objects.equals(exp, act)) { passes++; System.out.println("PASS  " + name + " = " + act); }
        else { fails++; System.out.println("FAIL  " + name + " expected " + exp + " got " + act); }
    }
    static final PointsConfig C = PointsConfig.defaults();
    static int cost(String id, int lvl) { return CostRules.enchantCost(C, "minecraft:" + id, lvl); }
    static DisableSelector.Candidate cand(long id, int cost, long acq) { return new DisableSelector.Candidate(id, cost, acq, (int) id); }

    public static void main(String[] a) {
        System.out.println("== Defaults validate ==");
        eq("default config errors", List.of(), C.validate());

        System.out.println("== Spec cost tables (Test J) ==");
        eq("Mending I", 2, cost("mending", 1));
        eq("Mending V (fixed, not per level)", 2, cost("mending", 5));
        for (int[] p : new int[][]{{1,2},{2,4},{5,10},{10,20}}) eq("Lunge " + p[0], p[1], cost("lunge", p[0]));
        for (int[] p : new int[][]{{1,4},{2,8},{5,20},{10,40}}) eq("Breach " + p[0], p[1], cost("breach", p[0]));
        for (int[] p : new int[][]{{1,1},{4,4},{5,5},{6,7},{7,9},{8,11},{10,15}}) eq("Protection " + p[0], p[1], cost("protection", p[0]));
        for (int[] p : new int[][]{{1,1},{3,3},{4,5},{5,7},{7,11},{8,14},{9,17},{10,20}}) eq("Sharpness " + p[0], p[1], cost("sharpness", p[0]));
        eq("Unbreaking III", 3, cost("unbreaking", 3));
        eq("Knockback II", 2, cost("knockback", 2));
        eq("Feather Falling IV", 4, cost("feather_falling", 4));
        eq("Efficiency V", 5, cost("efficiency", 5));
        eq("Unbreaking X", 10, cost("unbreaking", 10));
        eq("Efficiency X", 10, cost("efficiency", 10));
        eq("Sharpness 255 not clipped (3+8+248*3)", 3 + 8 + 248 * 3, cost("sharpness", 255));
        eq("Sample sword (Sharp X + Unb V + KB II + Mending)", 29, cost("sharpness",10)+cost("unbreaking",5)+cost("knockback",2)+cost("mending",1));

        System.out.println("== Test A / B totals ==");
        eq("Test A 7+4+3", 14, cost("sharpness",5)+cost("protection",4)+cost("unbreaking",3));
        eq("Test B 10+10", 20, cost("unbreaking",10)+cost("efficiency",10));
        eq("Sec.5 Sharp X + Unb X over by 10", 30, cost("sharpness",10)+cost("unbreaking",10));

        System.out.println("== Test C: 19 pts + new 8-pt item => 27/20, new item disabled ==");
        List<DisableSelector.Candidate> inv = new ArrayList<>(List.of(cand(1, 7, 100), cand(2, 4, 101), cand(3, 5, 102), cand(4, 3, 103), cand(5, 8, 999)));
        eq("total", 27, inv.stream().mapToInt(DisableSelector.Candidate::cost).sum());
        eq("disabled = only new item", Set.of(5L), DisableSelector.select(inv, 20));

        System.out.println("== Test F: drop items, back to 19 => nothing disabled ==");
        inv.removeIf(c -> c.id() == 3 || c.id() == 4); // -8 pts... leaves 19
        eq("total", 19, inv.stream().mapToInt(DisableSelector.Candidate::cost).sum());
        eq("disabled", Set.of(), DisableSelector.select(inv, 20));

        System.out.println("== Test G: lower max 30 -> 20 while holding 25 ==");
        List<DisableSelector.Candidate> g = List.of(cand(1, 10, 1), cand(2, 8, 2), cand(3, 7, 3));
        eq("max 30 nothing disabled", Set.of(), DisableSelector.select(g, 30));
        eq("max 20 disables newest only (25-7=18<=20)", Set.of(3L), DisableSelector.select(g, 20));
        List<DisableSelector.Candidate> g2 = List.of(cand(1, 10, 1), cand(2, 10, 2), cand(3, 5, 3));
        eq("needs 2 items: 25 -> drop 5 (20) ok", Set.of(3L), DisableSelector.select(g2, 20));
        List<DisableSelector.Candidate> g3 = List.of(cand(1, 10, 1), cand(2, 12, 2), cand(3, 2, 3));
        eq("24 -> 22 still >20, also disable next newest", new LinkedHashSet<>(List.of(3L, 2L)), DisableSelector.select(g3, 20));

        System.out.println("== Test H: raise max 20 -> 30 with total 25 ==");
        eq("re-enabled", Set.of(), DisableSelector.select(g, 30));

        System.out.println("== Determinism ==");
        eq("same input same output", DisableSelector.select(g3, 20), DisableSelector.select(new ArrayList<>(g3), 20));

        System.out.println("== Config validation reports errors ==");
        PointsConfig bad = PointsConfig.defaults();
        bad.tieredCosts.get("minecraft:sharpness").add(new PointsConfig.Tier(5, 9, 1));
        bad.fixedCosts.put("minecraft:lunge", 1);
        bad.perLevelCosts.put("NotAnId", 1);
        bad.maxPoints = -1;
        List<String> errs = bad.validate();
        errs.forEach(e -> System.out.println("      error: " + e));
        eq("4+ errors detected", true, errs.size() >= 4);

        System.out.println("\n" + passes + " passed, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
