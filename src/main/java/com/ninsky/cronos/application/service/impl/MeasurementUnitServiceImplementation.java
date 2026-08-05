package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.request.core.CreateMeasurementUnitRequest;
import com.ninsky.cronos.application.request.core.UpdateMeasurementUnitRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.MeasurementUnitResponse;
import com.ninsky.cronos.application.service.MeasurementUnitService;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.DataIntegrityViolationException;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MeasurementUnitServiceImplementation implements MeasurementUnitService {

    private final MeasurementUnitRepositoryPort measurementUnitRepository;
    private final UnitTypeRepositoryPort unitTypeRepository;
    private final UserRepositoryPort userRepository;

    /**
     * Creates a new category
     */
    @Transactional
    @Override
    public MeasurementUnitResponse createMeasurementUnit(CreateMeasurementUnitRequest request, String authentication) {
        if (measurementUnitRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("MeasurementUnit already exists with name: " + request.name());
        }

        UnitType unitType = unitTypeRepository.findById(request.unitTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("UnitType not found with id: " + request.unitTypeId()));

        if (request.isBaseUnit()) {
            Optional<MeasurementUnit> currentBase = measurementUnitRepository.findByUnitTypeIdAndIsBaseUnitTrue(unitType.getId());
            if (currentBase.isPresent()) {
                throw new DataIntegrityViolationException(
                        String.format("Data integrity error: UnitType '%s' already has a base unit ('%s').",
                                unitType.getName(), currentBase.get().getName())
                );
            }
        }

        MeasurementUnit unit = MeasurementUnit.builder().codeIdentity(request.codeIdentity())
                .name(request.name()).namePlural(request.namePlural()).multiplierToBase(request.multiplierToBase())
                .unitTypeId(unitType.getId()).isBaseUnit(request.isBaseUnit()).isSystemDefault(true)
                .build();

        unit = measurementUnitRepository.save(unit);

        log.info("MeasurementUnit created: {}, code: {} ", unit.getName(), unit.getCodeIdentity());

        return mapToResponse(unit, unitType);
    }


    /**
     * Gets system measurement units by user - paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<MeasurementUnitResponse> getUserMeasurementUnits(Pageable pageable, String username) {
        log.info("Fetching paginated Measurement Unit by User: {}. Page: {}, Size: {}", username, pageable.getPageNumber(), pageable.getPageSize());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return measurementUnitRepository.findSystemMeasurementUnits(pageable).map(this::mapToResponse);
    }

    /**
     * Gets system measurement units by system - paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<MeasurementUnitResponse> getSystemMeasurementUnits(Pageable pageable, String username) {
        log.info("Fetching paginated Measurement Unit by System. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return measurementUnitRepository.findSystemMeasurementUnits(pageable).map(this::mapToResponse);
    }

    /**
     * Update a MeasurementUnit
     */
    @Transactional
    @Override
    public MeasurementUnitResponse updateMeasurementUnit(UpdateMeasurementUnitRequest request, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        MeasurementUnit existingUnit = measurementUnitRepository.findById(request.id())
                .orElseThrow(() -> new ResourceNotFoundException("MeasurementUnit not found with id: " + request.id()));

        if (!existingUnit.getName().equalsIgnoreCase(request.name())
                && measurementUnitRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("Another MeasurementUnit already exists with name: " + request.name());
        }

        UnitType unitType = unitTypeRepository.findById(request.unitTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("UnitType not found with id: " + request.unitTypeId()));

        if (request.isBaseUnit()) {
            // Si la estamos marcando como base, verificamos que no exista OTRA unidad base en la misma categoría
            Optional<MeasurementUnit> currentBase = measurementUnitRepository.findByUnitTypeIdAndIsBaseUnitTrue(unitType.getId());
            if (currentBase.isPresent() && !currentBase.get().getId().equals(request.id())) {
                throw new DataIntegrityViolationException(
                        String.format("Cannot set as base. UnitType '%s' already has '%s' as its base unit.",
                                unitType.getName(), currentBase.get().getName())
                );
            }
        } else {
            // Si la estamos marcando como NO base, y actualmente SÍ lo es, debemos bloquearlo.
            // Regla: No puedes dejar una categoría sin base. Debes asignar la base a otra unidad primero.
            if (existingUnit.isBaseUnit()) {
                throw new DataIntegrityViolationException(
                        "Cannot remove the base unit flag. Please assign another measurement unit as the base for this category first to ensure data integrity."
                );
            }
        }

        existingUnit.setCodeIdentity(request.codeIdentity());
        existingUnit.setName(request.name());
        existingUnit.setNamePlural(request.namePlural());
        existingUnit.setMultiplierToBase(request.multiplierToBase());
        existingUnit.setUnitTypeId(unitType.getId());
        existingUnit.setBaseUnit(request.isBaseUnit());
        existingUnit.setStatus(request.status());

        measurementUnitRepository.save(existingUnit);

        log.info("MeasurementUnit updated: {}, code: {}", existingUnit.getName(), existingUnit.getCodeIdentity());

        return mapToResponse(existingUnit, unitType);
    }

    @Transactional
    public void changeStatus(Long id, ChangeStatusRequest request) {
        log.info("Updating the status of MeasurementUnit with ID {} to {}", id, request.status());

        int updatedRows = measurementUnitRepository.updateStatus(id, request.status());

        if (updatedRows == 0) {
            log.warn("Attempt to change the status of a non-existent MeasurementUnit: {}", id);
            throw new ResourceNotFoundException("La Unidad de Medida con ID " + id + " no existe.");
        }
    }

    private MeasurementUnitResponse mapToResponse(MeasurementUnit unit, UnitType unitType) {
        return MeasurementUnitResponse.builder()
                .id(unit.getId()).codeIdentity(unit.getCodeIdentity())
                .name(unit.getName()).namePlural(unit.getNamePlural())
                .unitType(unitType.getName())
                .multiplierToBase(unit.getMultiplierToBase())
                .isBaseUnit(unit.isBaseUnit()).isSystemDefault(unit.isSystemDefault())
                .status(unit.getStatus().name())
                .build();
    }

    private MeasurementUnitResponse mapToResponse(MeasurementUnit unit) {
        UnitType unitType = unitTypeRepository.findById(unit.getUnitTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("UnitType not found with id: " + unit.getUnitTypeId()));
        return mapToResponse(unit, unitType);
    }
}
