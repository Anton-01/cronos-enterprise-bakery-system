package com.ninsky.cronos.iam.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Tenant timezone conversions (N7): legacy TIMESTAMP columns hold America/Mexico_City wall time. */
public final class TenantTime {

    public static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

    private TenantTime() {
    }

    public static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(ZONE).toInstant();
    }

    public static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** Microsecond precision, as PostgreSQL stores it. */
    public static LocalDateTime nowLocal(Clock clock) {
        return LocalDateTime.now(clock.withZone(ZONE)).truncatedTo(ChronoUnit.MICROS);
    }

    public static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
