package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.event.UnitCatalogChangedEvent;
import com.ninsky.cronos.application.request.core.MeasurementUnitRequest;
import com.ninsky.cronos.application.request.core.UnitConversionRequest;
import com.ninsky.cronos.application.response.core.MeasurementUnitOptionResponse;
import com.ninsky.cronos.application.response.core.MeasurementUnitResponse;
import com.ninsky.cronos.application.response.core.UnitConversionResponse;
import com.ninsky.cronos.application.service.MeasurementUnitService;
import com.ninsky.cronos.application.service.UnitCatalogResponseMapper;
import com.ninsky.cronos.application.service.UnitConversionService;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.application.service.catalog.UnitCatalogCaches;
import com.ninsky.cronos.application.service.catalog.UnitCatalogPolicy;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.FieldChange;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.MeasurementUnitView;
import com.ninsky.cronos.domain.model.core.UnitConversionResult;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitSearchCriteria;
import com.ninsky.cronos.domain.port.core.MeasurementUnitUsagePort;
import com.ninsky.cronos.domain.port.core.UnitCatalogLockPort;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.CatalogException;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueryCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
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
import java.util.UUID;

import static com.ninsky.cronos.application.service.catalog.RuleViolation.throwFirst;

/**
 * Measurement units are system-wide master data: every write is serialized through the catalog
 * lock, validated by {@link UnitCatalogPolicy} (same rules as the .xlsx import) and recorded in the
 * audit ledger. Units created here are system units ({@code isSystemDefault = true}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementUnitServiceImplementation implements MeasurementUnitService {

    private final MeasurementUnitRepositoryPort measurementUnitRepository;
    private final UnitTypeRepositoryPort unitTypeRepository;
    private final MeasurementUnitUsagePort usagePort;
    private final IngredientQueryCustomRepository ingredientQueries;
    private final UnitConversionService unitConversionService;
    private final UnitCatalogLockPort catalogLock;
    private final CatalogAuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public MeasurementUnitResponse createMeasurementUnit(MeasurementUnitRequest request, Actor actor) {
        catalogLock.lockForWrite();
        MeasurementUnit candidate = applyRequest(MeasurementUnit.builder().isSystemDefault(true).build(), request);
        UnitCatalogPolicy policy = policy(Set.of());
        throwFirst(policy.admitMeasurementUnit(candidate));
        throwFirst(policy.verifyBaseUnits());

        MeasurementUnit saved = measurementUnitRepository.save(candidate);
        auditTrail.record(actor, AuditAction.MEASUREMENT_UNIT_CREATED, CatalogAuditTrail.TARGET_MEASUREMENT_UNIT, saved.getId(),
                FieldChange.created(UnitCatalogResponseMapper.snapshot(saved)), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.MEASUREMENT_UNIT_CREATED.name()));
        log.info("MeasurementUnit created: id={}, code={}, by={}", saved.getId(), saved.getCodeIdentity(), actor.username());
        return toResponse(saved.getId(), false);
    }

    @Override
    @Transactional
    public MeasurementUnitResponse updateMeasurementUnit(Long id, MeasurementUnitRequest request, Actor actor) {
        catalogLock.lockForWrite();
        MeasurementUnit current = requireUnit(id);
        MeasurementUnit candidate = applyRequest(current.toBuilder().build(), request);
        UnitCatalogPolicy policy = policy(usagePort.findReferencedUnitIds(Set.of(id)));
        throwFirst(policy.admitMeasurementUnit(candidate));
        throwFirst(policy.verifyBaseUnits());

        MeasurementUnit saved = measurementUnitRepository.save(candidate);
        auditTrail.record(actor, AuditAction.MEASUREMENT_UNIT_UPDATED, CatalogAuditTrail.TARGET_MEASUREMENT_UNIT, id,
                FieldChange.diff(UnitCatalogResponseMapper.snapshot(current), UnitCatalogResponseMapper.snapshot(saved)), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.MEASUREMENT_UNIT_UPDATED.name()));
        log.info("MeasurementUnit updated: id={}, by={}", id, actor.username());
        return toResponse(id, policy.isReferenced(id));
    }

    @Override
    @Transactional(readOnly = true)
    public MeasurementUnitResponse getMeasurementUnit(Long id) {
        return toResponse(id, usagePort.isReferenced(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MeasurementUnitResponse> searchMeasurementUnits(MeasurementUnitSearchCriteria criteria, Pageable pageable) {
        Page<MeasurementUnitView> page = measurementUnitRepository.search(criteria, pageable);
        Set<Long> referenced = usagePort.findReferencedUnitIds(page.getContent().stream().map(view -> view.unit().getId()).toList());
        return page.map(view -> UnitCatalogResponseMapper.toResponse(view, referenced.contains(view.unit().getId())));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = UnitCatalogCaches.MEASUREMENT_UNITS, key = "'selectable'")
    public List<MeasurementUnitOptionResponse> getSelectableCatalog() {
        return measurementUnitRepository.findSelectableViews().stream().map(UnitCatalogResponseMapper::toOption).toList();
    }

    @Override
    @Transactional
    public void deleteMeasurementUnit(Long id, Actor actor) {
        catalogLock.lockForWrite();
        MeasurementUnit current = requireUnit(id);
        throwFirst(policy(usagePort.findReferencedUnitIds(Set.of(id))).checkMeasurementUnitDeletion(current));

        measurementUnitRepository.delete(current);
        auditTrail.record(actor, AuditAction.MEASUREMENT_UNIT_DELETED, CatalogAuditTrail.TARGET_MEASUREMENT_UNIT, id,
                FieldChange.diff(UnitCatalogResponseMapper.snapshot(current), Map.of()), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.MEASUREMENT_UNIT_DELETED.name()));
        log.info("MeasurementUnit soft-deleted: id={}, by={}", id, actor.username());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, RecordStatus status, Actor actor) {
        catalogLock.lockForWrite();
        MeasurementUnit current = requireUnit(id);
        if (current.getStatus() == status) {
            return;
        }
        throwFirst(policy(Set.of()).admitMeasurementUnit(current.toBuilder().status(status).build()));

        measurementUnitRepository.updateStatus(id, status, actor.username());
        auditTrail.record(actor, AuditAction.MEASUREMENT_UNIT_STATUS_CHANGED, CatalogAuditTrail.TARGET_MEASUREMENT_UNIT, id,
                FieldChange.diff(Map.of("status", current.getStatus().name()), Map.of("status", status.name())), null);
        eventPublisher.publishEvent(new UnitCatalogChangedEvent(AuditAction.MEASUREMENT_UNIT_STATUS_CHANGED.name()));
        log.info("MeasurementUnit status changed: id={}, {} -> {}, by={}", id, current.getStatus(), status, actor.username());
    }

    @Override
    @Transactional(readOnly = true)
    public UnitConversionResponse convert(UnitConversionRequest request, Actor actor) {
        MeasurementUnit from = requireUnit(request.fromUnitId());
        MeasurementUnit to = requireUnit(request.toUnitId());
        UUID ingredientId = request.rawMaterialId() == null ? null : requireVisibleIngredient(request.rawMaterialId(), actor);

        UnitConversionResult result = unitConversionService.convertWithTrace(request.quantity(), from, to, ingredientId);
        return new UnitConversionResponse(
                UnitCatalogResponseMapper.plain(request.quantity()),
                from.getId(), from.getCodeIdentity(),
                to.getId(), to.getCodeIdentity(),
                UnitCatalogResponseMapper.plain(result.quantity()),
                result.path().name(),
                result.densityRuleId(),
                request.rawMaterialId());
    }

    private static MeasurementUnit applyRequest(MeasurementUnit target, MeasurementUnitRequest request) {
        target.setCodeIdentity(request.codeIdentity());
        target.setName(request.name());
        target.setNamePlural(request.namePlural());
        target.setUnitTypeId(request.unitTypeId());
        target.setMultiplierToBase(request.multiplierToBase());
        target.setBaseUnit(request.isBaseUnit());
        return target;
    }

    private MeasurementUnit requireUnit(Long id) {
        return measurementUnitRepository.findById(id).orElseThrow(() -> CatalogException.notFound("catalog.unit.notFound", id));
    }

    /** Own or SYSTEM ingredient; someone else's answers like a missing one (no cross-tenant oracle). */
    private UUID requireVisibleIngredient(UUID ingredientId, Actor actor) {
        return ingredientQueries.find(actor.userId(), KitchenMessages.language(), ingredientId)
                .map(IngredientQueryCustomRepository.Row::id)
                .orElseThrow(() -> CatalogException.notFound("catalog.conversion.rawMaterialNotFound", ingredientId));
    }

    private MeasurementUnitResponse toResponse(Long id, boolean inUse) {
        MeasurementUnitView view = measurementUnitRepository.findViewById(id)
                .orElseThrow(() -> CatalogException.notFound("catalog.unit.notFound", id));
        return UnitCatalogResponseMapper.toResponse(view, inUse);
    }

    private UnitCatalogPolicy policy(Set<Long> referencedUnitIds) {
        return UnitCatalogPolicy.of(unitTypeRepository.findAll(), measurementUnitRepository.findAll(), referencedUnitIds);
    }
}
