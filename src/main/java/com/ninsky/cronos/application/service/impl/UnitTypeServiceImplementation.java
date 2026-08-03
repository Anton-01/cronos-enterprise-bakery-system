package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.request.core.UnitTypeRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.UnitTypeResponse;
import com.ninsky.cronos.application.service.UnitTypeService;
import com.ninsky.cronos.domain.entity.core.UnitType;
import com.ninsky.cronos.infrastructure.exception.DataIntegrityViolationException;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.auth.UserRepository;
import com.ninsky.cronos.infrastructure.persistence.core.MeasurementUnitRepository;
import com.ninsky.cronos.infrastructure.persistence.core.UnitTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Slf4j
@RequiredArgsConstructor
public class UnitTypeServiceImplementation implements UnitTypeService {

    private final UnitTypeRepository unitTypeRepository;
    private final MeasurementUnitRepository measurementUnitRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new unitType
     */
    @Transactional
    @Override
    @CacheEvict(value = "unitTypes", allEntries = true)
    public UnitTypeResponse createUnitType(UnitTypeRequest request) {
        if (unitTypeRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("UnitType already exists in our records.");
        }

        UnitType unit = UnitType.builder().codeIdentity(request.codeIdentity())
                .name(request.name()).dimension(request.dimension()).build();

        unit = unitTypeRepository.save(unit);

        log.info("UnitType created: {}, codeIdentity: {} ", unit.getName(), request.codeIdentity());

        return mapToResponse(unit);
    }

    /**
     * Gets all unitTypes available for users
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "unitTypesSystem", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    public Page<UnitTypeResponse> getUnitTypes(Pageable pageable) {
        log.debug("Caché MISS - Fetching from DB. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return unitTypeRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Update a new unitType
     */
    @Transactional
    @Override
    @CacheEvict(value = "unitTypes", allEntries = true)
    public UnitTypeResponse updateUnitType(String username, Long unitTypeId, UnitTypeRequest request) {
        userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado o token inválido"));

        UnitType unit = unitTypeRepository.findById(unitTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("UnitType not found with id: " + unitTypeId));

        if (!unit.getCodeIdentity().equalsIgnoreCase(request.codeIdentity()) && unitTypeRepository.existsByCodeIdentityEqualsIgnoreCase(request.codeIdentity().trim())) {
            throw new DuplicateResourceException(String.format("The identification code: %s, is already in our records.", request.codeIdentity()));
        }

        if (!unit.getName().equalsIgnoreCase(request.name().trim()) && unitTypeRepository.existsByName(request.name().trim())) {
            throw new DuplicateResourceException("UnitType name already exists in our records.");
        }

        unit.setCodeIdentity(request.codeIdentity());
        unit.setName(request.name());
        unit.setDimension(request.dimension());

        unit = unitTypeRepository.save(unit);
        log.info("UnitType updated: ID {}, Name: {}", unit.getId(), unit.getName());

        return mapToResponse(unit);
    }

    @Override
    @Transactional
    @CacheEvict(value = "unitTypesSystem", allEntries = true)
    public void deleteUnitType(Long id) {
        log.debug("Starting business logic to soft-delete UnitType with ID: {}", id);

        UnitType unitType = unitTypeRepository.findById(id).orElseThrow(() -> {
            log.warn("Deletion failed: UnitType with ID '{}' not found or already deleted.", id);
            return new RuntimeException("The Unit Type with ID: " + id + " does not exist or has already been deleted.");
        });

        if (measurementUnitRepository.countByUnitType(unitType) > 0) {
            log.info("Deletion failed: UnitType with ID '{}' is already linked to Measurement Units.", id);
            throw new DataIntegrityViolationException(String.format("No es posible eliminar '%s' porque está vinculado a unidades de medida.", unitType.getName()));
        }
        unitTypeRepository.delete(unitType);

        log.info("Successfully soft-deleted UnitType with ID: {}", id);
    }

    @Transactional
    public void changeStatus(Long id, ChangeStatusRequest request) {
        log.info("Updating the status of UnitType with ID {} to {}", id, request.status());

        int updatedRows = unitTypeRepository.updateStatus(id, request.status());

        if (updatedRows == 0) {
            log.warn("Attempt to change the status of a non-existent UnitType: {}", id);
            throw new ResourceNotFoundException("El Tipo de Unidad con ID " + id + " no existe.");
        }
    }

    private UnitTypeResponse mapToResponse(UnitType unitType) {
        return UnitTypeResponse.builder().id(unitType.getId()).name(unitType.getName())
                .codeIdentity(unitType.getCodeIdentity()).dimension(unitType.getDimension())
                .status(unitType.getStatus().name()).build();
    }
}
