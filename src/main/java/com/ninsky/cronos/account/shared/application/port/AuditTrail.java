package com.ninsky.cronos.account.shared.application.port;

import com.ninsky.cronos.account.shared.domain.audit.AuditChange;

/**
 * Records a business audit event. Implementations must only persist once the surrounding
 * transaction commits, so a rolled-back write never leaves an audit row behind.
 */
public interface AuditTrail {

    void record(AuditChange change);
}
