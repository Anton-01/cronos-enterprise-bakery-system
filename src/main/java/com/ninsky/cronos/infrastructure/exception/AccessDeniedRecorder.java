package com.ninsky.cronos.infrastructure.exception;

import jakarta.servlet.http.HttpServletRequest;

/** Records a refused request in the audit ledger (spec §7.2 {@code ACCESS_DENIED}). */
public interface AccessDeniedRecorder {

    void record(HttpServletRequest request, String code);
}
