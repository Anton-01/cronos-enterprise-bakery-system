package com.ninsky.cronos.domain.entity.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Quote lifecycle (auth-quotes-calendar-notifications §4.1). The transition table is enum-keyed and immutable;
 * {@code CANCELED} of earlier releases became {@code REVOKED} (V27).
 */
public enum QuoteStatus {
    DRAFT, SENT, VIEWED, ACCEPTED, REJECTED, EXPIRED, REVOKED;

    private static final Map<QuoteStatus, Set<QuoteStatus>> ALLOWED;

    static {
        Map<QuoteStatus, Set<QuoteStatus>> allowed = new EnumMap<>(QuoteStatus.class);
        allowed.put(DRAFT, Collections.unmodifiableSet(EnumSet.of(SENT)));
        allowed.put(SENT, Collections.unmodifiableSet(EnumSet.of(VIEWED, ACCEPTED, REJECTED, EXPIRED, REVOKED)));
        allowed.put(VIEWED, Collections.unmodifiableSet(EnumSet.of(ACCEPTED, REJECTED, EXPIRED, REVOKED)));
        allowed.put(EXPIRED, Collections.unmodifiableSet(EnumSet.of(SENT)));
        allowed.put(ACCEPTED, Collections.unmodifiableSet(EnumSet.noneOf(QuoteStatus.class)));
        allowed.put(REJECTED, Collections.unmodifiableSet(EnumSet.noneOf(QuoteStatus.class)));
        allowed.put(REVOKED, Collections.unmodifiableSet(EnumSet.noneOf(QuoteStatus.class)));
        ALLOWED = Collections.unmodifiableMap(allowed);
    }

    private static final Set<QuoteStatus> EDITABLE = Collections.unmodifiableSet(EnumSet.of(DRAFT, SENT, VIEWED, EXPIRED));
    private static final Set<QuoteStatus> OPEN = Collections.unmodifiableSet(EnumSet.of(SENT, VIEWED));

    /** True when {@code target} is reachable from this status. */
    public boolean canMoveTo(QuoteStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** DRAFT, SENT, VIEWED and EXPIRED quotes can still be edited. */
    public boolean editable() {
        return EDITABLE.contains(this);
    }

    /** SENT or VIEWED: waiting for the client's answer. */
    public boolean open() {
        return OPEN.contains(this);
    }

    /** ACCEPTED, REJECTED and REVOKED never change again. */
    public boolean terminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** The open statuses. */
    public static Set<QuoteStatus> openStatuses() {
        return OPEN;
    }
}
