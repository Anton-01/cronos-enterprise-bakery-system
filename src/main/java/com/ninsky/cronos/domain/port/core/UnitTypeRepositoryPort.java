package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.UnitType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Soft-deleted rows are invisible to every method here. Every {@code excludeId} parameter is
 * nullable: {@code null} means "check against all rows" (create), an id means "ignore this row"
 * (update of that row).
 */
public interface UnitTypeRepositoryPort {

    UnitType save(UnitType unitType);

    Optional<UnitType> findById(Long id);

    /** The whole catalog — a handful of rows, used to validate bulk imports in memory. */
    List<UnitType> findAll();

    List<UnitType> findAllActive();

    Page<UnitType> search(UnitTypeSearchCriteria criteria, Pageable pageable);

    boolean existsByCodeIgnoreCase(String codeIdentity, Long excludeId);

    boolean existsByNameIgnoreCase(String name, Long excludeId);

    boolean existsByDimension(UnitDimension dimension, Long excludeId);

    /** Bulk UPDATE bypasses JPA auditing, so the actor is stamped into {@code updated_by} explicitly. */
    int updateStatus(Long id, RecordStatus status, String actor);

    /** Soft-deletes (JPA {@code @SQLDelete} intercepts this into an UPDATE, not a physical DELETE). */
    void delete(UnitType unitType);
}
