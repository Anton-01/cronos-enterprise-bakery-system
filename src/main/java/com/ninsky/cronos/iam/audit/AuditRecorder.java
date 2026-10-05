package com.ninsky.cronos.iam.audit;

/** Writes ledger entries (spec §7). */
public interface AuditRecorder {

    /** Joins the caller's transaction: a failed insert rolls the business change back (N4). */
    void record(AuditEvent event);

    /** Own transaction: survives the caller's rollback (denials, failed logins, system jobs). */
    void recordIndependently(AuditEvent event);
}
