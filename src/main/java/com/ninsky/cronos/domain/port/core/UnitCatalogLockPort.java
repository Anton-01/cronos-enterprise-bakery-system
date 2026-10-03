package com.ninsky.cronos.domain.port.core;

/**
 * Serializes writers of the unit catalog (two admins importing at once, or an import racing a
 * single-row edit). Must be called inside a transaction; the lock is released when it ends.
 */
public interface UnitCatalogLockPort {

    void lockForWrite();
}
