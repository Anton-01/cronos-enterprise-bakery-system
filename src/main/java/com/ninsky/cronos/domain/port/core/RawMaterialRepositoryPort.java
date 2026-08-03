package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.RawMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RawMaterialRepositoryPort {

    RawMaterial save(RawMaterial rawMaterial);

    Optional<RawMaterial> findById(UUID id);

    List<RawMaterial> findAllById(Iterable<UUID> ids);

    /**
     * Cross-aggregate read-model projection (joins Category for display) — returns the response
     * DTO directly rather than the domain aggregate, since it's a list/search screen, not an
     * aggregate load.
     */
    Page<RawMaterialListResponse> findAllForListByUserId(UUID userId, Pageable pageable);

    int updateStatus(UUID id, RecordStatus status);
}
