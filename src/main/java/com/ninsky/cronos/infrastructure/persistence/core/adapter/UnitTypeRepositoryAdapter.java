package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.domain.port.core.UnitTypeRepositoryPort;
import com.ninsky.cronos.domain.port.core.UnitTypeSearchCriteria;
import com.ninsky.cronos.infrastructure.persistence.core.UnitTypeJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.UnitTypeMapper;
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

@Component
public class UnitTypeRepositoryAdapter implements UnitTypeRepositoryPort {

    private static final SortWhitelist SORTABLE = new SortWhitelist(Map.of(
            "id", "id",
            "codeIdentity", "codeIdentity",
            "name", "name",
            "dimension", "dimension",
            "status", "status",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"));

    private final UnitTypeJpaRepository jpaRepository;
    private final UnitTypeMapper mapper;

    public UnitTypeRepositoryAdapter(UnitTypeJpaRepository jpaRepository, UnitTypeMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UnitType save(UnitType unitType) {
        UnitTypeJpaEntity saved = jpaRepository.save(mapper.toEntity(unitType));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UnitType> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<UnitType> findAll() {
        return jpaRepository.findAll(Sort.by("id")).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<UnitType> findAllActive() {
        return jpaRepository.findAllByStatusOrderByNameAsc(RecordStatus.ACTIVE).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Page<UnitType> search(UnitTypeSearchCriteria criteria, Pageable pageable) {
        return jpaRepository.findAll(matching(criteria), SORTABLE.translate(pageable)).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCodeIgnoreCase(String codeIdentity, Long excludeId) {
        return excludeId == null
                ? jpaRepository.existsByCodeIdentityIgnoreCase(codeIdentity)
                : jpaRepository.existsByCodeIdentityIgnoreCaseAndIdNot(codeIdentity, excludeId);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name, Long excludeId) {
        return excludeId == null
                ? jpaRepository.existsByNameIgnoreCase(name)
                : jpaRepository.existsByNameIgnoreCaseAndIdNot(name, excludeId);
    }

    @Override
    public boolean existsByDimension(UnitDimension dimension, Long excludeId) {
        return excludeId == null
                ? jpaRepository.existsByDimension(dimension)
                : jpaRepository.existsByDimensionAndIdNot(dimension, excludeId);
    }

    @Override
    public int updateStatus(Long id, RecordStatus status, String actor) {
        return jpaRepository.updateStatus(id, status, actor);
    }

    @Override
    public void delete(UnitType unitType) {
        jpaRepository.findById(unitType.getId()).ifPresent(jpaRepository::delete);
    }

    private static Specification<UnitTypeJpaEntity> matching(UnitTypeSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.search() != null) {
                String pattern = "%" + escapeLike(criteria.search().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("codeIdentity")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')));
            }
            if (criteria.dimension() != null) {
                predicates.add(cb.equal(root.get("dimension"), criteria.dimension()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
