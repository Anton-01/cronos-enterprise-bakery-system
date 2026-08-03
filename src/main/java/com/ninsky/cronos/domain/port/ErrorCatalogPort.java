package com.ninsky.cronos.domain.port;

import java.util.Optional;

/**
 * Outbound port for resolving database-backed error catalog entries.
 * Implemented by an infrastructure adapter; no framework or persistence detail leaks through here.
 */
public interface ErrorCatalogPort {

    Optional<ErrorCatalogEntry> findByCode(String errorCode);
}
