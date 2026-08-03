package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pure domain aggregate. {@code version} is carried through (not just a JPA implementation
 * detail) so the optimistic-lock check performed by {@link com.ninsky.cronos.infrastructure.persistence.core.entity.CategoryJpaEntity}'s
 * {@code @Version} field still works correctly on update.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    private Long id;
    private String name;
    private String description;
    @Builder.Default
    private Boolean isSystemDefault = true;
    @Builder.Default
    private Long version = 1L;
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;
}
