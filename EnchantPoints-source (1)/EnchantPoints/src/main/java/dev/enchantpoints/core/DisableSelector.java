package dev.enchantpoints.core;

import java.util.*;

/**
 * Deterministic rule for WHICH items get disabled.
 *
 *  - If total <= max: nothing is disabled (everything re-enables automatically).
 *  - Otherwise items are taken newest-acquired first (ties: higher inventory order first)
 *    until the points of the remaining, still-enabled items fit under max.
 *  - Disabled items still count toward the displayed total.
 */
public final class DisableSelector {
    private DisableSelector() {}

    public record Candidate(long id, int cost, long acquiredAt, int order) {}

    public static Set<Long> select(List<Candidate> items, int max) {
        long total = 0;
        for (Candidate c : items) total += c.cost();
        Set<Long> out = new LinkedHashSet<>();
        if (total <= max) return out;

        List<Candidate> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(Candidate::acquiredAt).reversed()
                .thenComparing(Comparator.comparingInt(Candidate::order).reversed()));
        long remaining = total;
        for (Candidate c : sorted) {
            if (remaining <= max) break;
            if (c.cost() <= 0) continue;
            out.add(c.id());
            remaining -= c.cost();
        }
        return out;
    }
}
