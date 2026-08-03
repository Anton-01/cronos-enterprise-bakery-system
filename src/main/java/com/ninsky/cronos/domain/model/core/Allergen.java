package com.ninsky.cronos.domain.model.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Allergen {

    private UUID id;
    private String name;
    private String alternativeName;
    private String description;
    @Builder.Default
    private Boolean isSystemDefault = true;
    @Builder.Default
    private Long version = 1L;
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;
}
