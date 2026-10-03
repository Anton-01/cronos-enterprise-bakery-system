package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.event.UnitCatalogChangedEvent;
import com.ninsky.cronos.application.request.core.UnitTypeRequest;
import com.ninsky.cronos.application.response.core.UnitTypeResponse;
import com.ninsky.cronos.application.service.UnitCatalogResponseMapper;
import com.ninsky.cronos.application.service.UnitTypeService;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.application.service.catalog.UnitCatalogCaches;
import com.ninsky.cronos.application.service.catalog.UnitCatalogPolicy;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.FieldChange;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.UnitCatalogLockPort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.domain.port.core.UnitTypeSearchCriteria;
import com.ninsky.cronos.infrastructure.exception.CatalogException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ninsky.cronos.application.service.catalog.RuleViolation.throwFirst;

/**
 * Unit types are system-wide master data: every write is serialized through the catalog lock,
 * validated by {@link UnitCatalogPolicy} and recorded in the audit ledger.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitTypeServiceImplementation implements UnitTypeService {

    private final UnitTypeRepositoryPort unitTypeRepository;
    private final MeasurementUnitRepositoryPort measurementUnitRepository;
    private final UnitCatalogLockPort catalogLock;
    private final CatalogAuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UnitTypeResponse createUnitType(UnitTypeRequest request, Actor actor) {
        catalogLock.lockForWrite();
        UnitType candidate = UnitType.builder()
                .codeIdentity(request.codeIdentity())
                .name(request.name())
                .dimension(request.dimension())
                .build();
        throwFirst(policy().admitUnitType(candidate));

        UnitType saved = unitTypeRepository.save(candidate);
        auditTrail.record(actor, AuditAction.UNIT_TYPE_CREATED, CatalogAuditTrail.TARGET_UNIT_TYPE, saved.getId(),
                FieldChange.created(UnitCatalogResponseMapper.snapshot(saved)), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.UNIT_TYPE_CREATED.name()));
        log.info("UnitType created: id={}, code={}, dimension={}, by={}", saved.getId(), saved.getCodeIdentity(), saved.getDimension(), actor.username());
        return UnitCatalogResponseMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UnitTypeResponse updateUnitType(Long id, UnitTypeRequest request, Actor actor) {
        catalogLock.lockForWrite();
        UnitType current = requireUnitType(id);
        UnitType candidate = current.toBuilder()
                .codeIdentity(request.codeIdentity())
                .name(request.name())
                .dimension(request.dimension())
                .build();
        throwFirst(policy().admitUnitType(candidate));

        UnitType saved = unitTypeRepository.save(candidate);
        auditTrail.record(actor, AuditAction.UNIT_TYPE_UPDATED, CatalogAuditTrail.TARGET_UNIT_TYPE, id,
                FieldChange.diff(UnitCatalogResponseMapper.snapshot(current), UnitCatalogResponseMapper.snapshot(saved)), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.UNIT_TYPE_UPDATED.name()));
        log.info("UnitType updated: id={}, by={}", id, actor.username());
        return UnitCatalogResponseMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitTypeResponse getUnitType(Long id) {
        return UnitCatalogResponseMapper.toResponse(requireUnitType(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UnitTypeResponse> searchUnitTypes(UnitTypeSearchCriteria criteria, Pageable pageable) {
        return unitTypeRepository.search(criteria, pageable).map(UnitCatalogResponseMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = UnitCatalogCaches.UNIT_TYPES, key = "'active'")
    public List<UnitTypeResponse> getActiveCatalog() {
        return unitTypeRepository.findAllActive().stream().map(UnitCatalogResponseMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void deleteUnitType(Long id, Actor actor) {
        catalogLock.lockForWrite();
        UnitType current = requireUnitType(id);
        throwFirst(policy().checkUnitTypeDeletion(current));

        unitTypeRepository.delete(current);
        auditTrail.record(actor, AuditAction.UNIT_TYPE_DELETED, CatalogAuditTrail.TARGET_UNIT_TYPE, id,
                FieldChange.diff(UnitCatalogResponseMapper.snapshot(current), Map.of()), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.UNIT_TYPE_DELETED.name()));
        log.info("UnitType soft-deleted: id={}, by={}", id, actor.username());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, RecordStatus status, Actor actor) {
        catalogLock.lockForWrite();
        UnitType current = requireUnitType(id);
        if (current.getStatus() == status) {
            return;
        }
        throwFirst(policy().admitUnitType(current.toBuilder().status(status).build()));

        unitTypeRepository.updateStatus(id, status, actor.username());
        auditTrail.record(actor, AuditAction.UNIT_TYPE_STATUS_CHANGED, CatalogAuditTrail.TARGET_UNIT_TYPE, id,
                FieldChange.diff(Map.of("status", current.getStatus().name()), Map.of("status", status.name())), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.UNIT_TYPE_STATUS_CHANGED.name()));
        log.info("UnitType status changed: id={}, {} -> {}, by={}", id, current.getStatus(), status, actor.username());
    }

    private UnitType requireUnitType(Long id) {
        return unitTypeRepository.findById(id).orElseThrow(() -> CatalogException.notFound("catalog.unitType.notFound", id));
    }

    /** Unit-type rules never depend on unit usage, so no usage lookup is needed for this snapshot. */
    private UnitCatalogPolicy policy() {
        return UnitCatalogPolicy.of(unitTypeRepository.findAll(), measurementUnitRepository.findAll(), Set.of());
    }
}
