package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSearchCriteria;
import com.ninsky.cronos.infrastructure.persistence.auth.RoleJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.UserMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final UserMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, RoleJpaRepository roleJpaRepository, UserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        Set<RoleJpaEntity> roles = user.getRoleIds().isEmpty()
                ? Set.of()
                : StreamSupport.stream(roleJpaRepository.findAllById(user.getRoleIds()).spliterator(), false).collect(Collectors.toSet());
        UserJpaEntity saved = jpaRepository.save(mapper.toEntity(user, roles));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsernameAndIdNot(String username, UUID userId) {
        return jpaRepository.existsByUsernameAndIdNot(username, userId);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, UUID userId) {
        return jpaRepository.existsByEmailAndIdNot(email, userId);
    }

    @Override
    public List<User> findExpiredLockedAccounts(LocalDateTime now) {
        return jpaRepository.findExpiredLockedAccounts(now).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Page<User> search(UserSearchCriteria criteria, Pageable pageable) {
        return jpaRepository.findAll(toSpecification(criteria), pageable).map(mapper::toDomain);
    }

    private Specification<UserJpaEntity> toSpecification(UserSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), criteria.enabled()));
            }
            if (StringUtils.hasText(criteria.roleName())) {
                Join<Object, Object> roles = root.join("roles");
                predicates.add(cb.equal(cb.upper(roles.get("name")), criteria.roleName().toUpperCase()));
            }
            if (StringUtils.hasText(criteria.search())) {
                String likePattern = "%" + criteria.search().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), likePattern),
                        cb.like(cb.lower(root.get("email")), likePattern)
                ));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
