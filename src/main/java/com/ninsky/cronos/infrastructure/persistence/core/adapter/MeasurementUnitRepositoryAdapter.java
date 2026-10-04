package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.MeasurementUnitView;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitSearchCriteria;
import com.ninsky.cronos.infrastructure.persistence.core.MeasurementUnitJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.UnitTypeJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.MeasurementUnitMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.ninsky.cronos.infrastructure.persistence.core.adapter.UnitTypeRepositoryAdapter.escapeLike;

@Component
public class MeasurementUnitRepositoryAdapter implements MeasurementUnitRepositoryPort {

    private static final SortWhitelist SORTABLE = new SortWhitelist(Map.of(
            "id", "id",
            "codeIdentity", "codeIdentity",
            "name", "name",
            "namePlural", "namePlural",
            "multiplierToBase", "multiplierToBase",
            "status", "status",
            "unitType", "unitType.name",
            "dimension", "unitType.dimension",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"));

    private final MeasurementUnitJpaRepository jpaRepository;
    private final UnitTypeJpaRepository unitTypeJpaRepository;
    private final MeasurementUnitMapper mapper;

    public MeasurementUnitRepositoryAdapter(MeasurementUnitJpaRepository jpaRepository,
                                             UnitTypeJpaRepository unitTypeJpaRepository,
                                             MeasurementUnitMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.unitTypeJpaRepository = unitTypeJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public MeasurementUnit save(MeasurementUnit measurementUnit) {
        // unitTypeId is validated by the caller (UnitTypeRepositoryPort.findById) before save is
        // invoked, so a managed-reference lookup avoids a redundant SELECT here.
        UnitTypeJpaEntity unitType = unitTypeJpaRepository.getReferenceById(measurementUnit.getUnitTypeId());
        MeasurementUnitJpaEntity saved = jpaRepository.save(mapper.toEntity(measurementUnit, unitType));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<MeasurementUnit> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<MeasurementUnit> findAllById(Iterable<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<MeasurementUnit> findByCodeIdentity(String code) {
        return jpaRepository.findByCodeIdentity(code).map(mapper::toDomain);
    }

    @Override
    public List<MeasurementUnit> findAll() {
        return jpaRepository.findAll(Sort.by("id")).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<MeasurementUnitView> findViewById(Long id) {
        return jpaRepository.findWithUnitTypeById(id).map(mapper::toView);
    }

    @Override
    public Page<MeasurementUnitView> search(MeasurementUnitSearchCriteria criteria, Pageable pageable) {
        return jpaRepository.findAll(matching(criteria), SORTABLE.translate(pageable)).map(mapper::toView);
    }

    @Override
    public List<MeasurementUnitView> findSelectableViews() {
        return jpaRepository.findAllSelectable(RecordStatus.ACTIVE).stream().map(mapper::toView).toList();
    }

    @Override
    public Optional<MeasurementUnit> findBaseUnitOf(Long unitTypeId) {
        return jpaRepository.findByUnitTypeIdAndIsBaseUnitTrue(unitTypeId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String codeIdentity, Long excludeId) {
        return excludeId == null
                ? jpaRepository.existsByCodeIdentity(codeIdentity)
                : jpaRepository.existsByCodeIdentityAndIdNot(codeIdentity, excludeId);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name, Long excludeId) {
        return excludeId == null
                ? jpaRepository.existsByNameIgnoreCase(name)
                : jpaRepository.existsByNameIgnoreCaseAndIdNot(name, excludeId);
    }

    @Override
    public long countByUnitTypeId(Long unitTypeId) {
        return jpaRepository.countByUnitTypeId(unitTypeId);
    }

    @Override
    public long countByUnitTypeIdAndStatus(Long unitTypeId, RecordStatus status) {
        return jpaRepository.countByUnitTypeIdAndStatus(unitTypeId, status);
    }

    @Override
    public int updateStatus(Long id, RecordStatus status, String actor) {
        return jpaRepository.updateStatus(id, status, actor);
    }

    @Override
    public void delete(MeasurementUnit measurementUnit) {
        jpaRepository.findById(measurementUnit.getId()).ifPresent(jpaRepository::delete);
    }

    private static Specification<MeasurementUnitJpaEntity> matching(MeasurementUnitSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.search() != null) {
                String pattern = "%" + escapeLike(criteria.search().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("codeIdentity")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("namePlural")), pattern, '\\')));
            }
            if (criteria.unitTypeId() != null) {
                predicates.add(cb.equal(root.get("unitType").get("id"), criteria.unitTypeId()));
            }
            if (criteria.dimension() != null) {
                Join<MeasurementUnitJpaEntity, UnitTypeJpaEntity> unitType = root.join("unitType");
                predicates.add(cb.equal(unitType.get("dimension"), criteria.dimension()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
