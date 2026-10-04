package com.ninsky.cronos.application.event;

/**
 * Published by every committed-to-be write on the unit catalog (single-row or bulk import).
 * {@link com.ninsky.cronos.application.listener.UnitCatalogCacheEvictionListener} drops the cached
 * pickers only after commit — evicting earlier would let a concurrent read re-cache the old rows.
 */
public record UnitCatalogChangedEvent(String reason) {
}
