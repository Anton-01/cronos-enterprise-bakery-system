package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pure domain aggregate — no JPA, no Spring. Persisted via {@link com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity}
 * through {@link com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitType {

    private Long id;
    private String codeIdentity;
    private String name;
    private String dimension; // MASS, VOLUME, COUNT, LENGTH
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;
}
