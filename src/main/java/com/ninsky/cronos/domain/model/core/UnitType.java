package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Pure domain aggregate — no JPA, no Spring. Persisted via {@link com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity}
 * through {@link com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort}.
 * <p>
 * At most one non-deleted unit type exists per {@link UnitDimension} (V8 exclusion constraint), so every
 * unit of a dimension shares one base unit and same-dimension conversions are always linear.
 * The audit fields are read-only here: they are filled by JPA auditing and only surfaced for display.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnitType {

    private Long id;
    private String codeIdentity;
    private String name;
    private UnitDimension dimension;
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }
}
