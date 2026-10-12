package com.ninsky.cronos.identity.domain;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Progressive lock durations: the n-th lock lasts {@code lockoutMinutes × multiplier^(n-1)}, capped by
 * {@code maxMinutes}. Precomputed as a {@link NavigableMap} keyed by lock count; lookups are {@code floorEntry}, so
 * every count beyond the cap reuses the capped duration.
 */
public final class LockoutSchedule {

    private final NavigableMap<Integer, Duration> byLockCount;

    private LockoutSchedule(NavigableMap<Integer, Duration> byLockCount) {
        this.byLockCount = Collections.unmodifiableNavigableMap(byLockCount);
    }

    /**
     * @throws IllegalArgumentException when a parameter is not positive
     */
    public static LockoutSchedule of(int lockoutMinutes, int multiplier, int maxMinutes) {
        if (lockoutMinutes < 1 || multiplier < 1 || maxMinutes < 1) {
            throw new IllegalArgumentException("Lockout parameters must be positive");
        }
        NavigableMap<Integer, Duration> schedule = new TreeMap<>();
        long minutes = Math.min(lockoutMinutes, maxMinutes);
        int count = 1;
        schedule.put(count, Duration.ofMinutes(minutes));
        while (minutes < maxMinutes && multiplier > 1) {
            minutes = Math.min(minutes * multiplier, maxMinutes);
            schedule.put(++count, Duration.ofMinutes(minutes));
        }
        return new LockoutSchedule(schedule);
    }

    /** Duration of lock number {@code lockCount} (1 = first lock). */
    public Duration durationFor(int lockCount) {
        Map.Entry<Integer, Duration> entry = byLockCount.floorEntry(Math.max(1, lockCount));
        return entry.getValue();
    }

    /** The precomputed steps (for diagnostics and tests). */
    public NavigableMap<Integer, Duration> steps() {
        return byLockCount;
    }
}
