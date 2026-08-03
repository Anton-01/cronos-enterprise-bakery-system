package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.UnitType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UnitTypeRepositoryPort {

    UnitType save(UnitType unitType);

    Optional<UnitType> findById(Long id);

    Optional<UnitType> findByName(String name);

    boolean existsByName(String name);

    boolean existsByCodeIdentityEqualsIgnoreCase(String codeIdentity);

    Page<UnitType> findAll(Pageable pageable);

    int updateStatus(Long id, RecordStatus status);

    /** Soft-deletes (JPA {@code @SQLDelete} intercepts this into an UPDATE, not a physical DELETE). */
    void delete(UnitType unitType);
}
