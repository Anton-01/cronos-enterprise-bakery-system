package com.ninsky.cronos.dashboard;

/**
 * The four dashboard counters (performance-latency §5.2), each with the scope of its module's {@code stats}: the caller's
 * live recipes, live quotes, visible active ingredients (library plus own) and fixed costs.
 */
public record DashboardSummary(long recipes, long quotes, long ingredients, long fixedCosts) {
}
