package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.MeasurementUnitView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Soft-deleted rows are invisible to every method here. Every {@code excludeId} parameter is
 * nullable: {@code null} means "check against all rows" (create), an id means "ignore this row"
 * (update of that row). Codes compare case-sensitively ({@code T} ≠ {@code t}); names do not.
 */
public interface MeasurementUnitRepositoryPort {

    MeasurementUnit save(MeasurementUnit measurementUnit);

    Optional<MeasurementUnit> findById(Long id);

    List<MeasurementUnit> findAllById(Iterable<Long> ids);

    Optional<MeasurementUnit> findByCodeIdentity(String code);

    /** The whole catalog — a few dozen rows, used to validate bulk imports in memory. */
    List<MeasurementUnit> findAll();

    Optional<MeasurementUnitView> findViewById(Long id);

    Page<MeasurementUnitView> search(MeasurementUnitSearchCriteria criteria, Pageable pageable);

    /** Active units whose unit type is active too: exactly what a recipe may pick from. */
    List<MeasurementUnitView> findSelectableViews();

    Optional<MeasurementUnit> findBaseUnitOf(Long unitTypeId);

    boolean existsByCode(String codeIdentity, Long excludeId);

    boolean existsByNameIgnoreCase(String name, Long excludeId);

    long countByUnitTypeId(Long unitTypeId);

    long countByUnitTypeIdAndStatus(Long unitTypeId, RecordStatus status);

    /** Bulk UPDATE bypasses JPA auditing, so the actor is stamped into {@code updated_by} explicitly. */
    int updateStatus(Long id, RecordStatus status, String actor);

    /** Soft-deletes (JPA {@code @SQLDelete} intercepts this into an UPDATE, not a physical DELETE). */
    void delete(MeasurementUnit measurementUnit);
}
