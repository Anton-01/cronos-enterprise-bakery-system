package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSearchCriteria;
import com.ninsky.cronos.infrastructure.persistence.auth.RoleJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.UserMapper;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private static final String EMAIL_FIELD_CONTEXT = "email";

    private final UserJpaRepository jpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final UserMapper mapper;
    private final BlindIndexService blindIndexService;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, RoleJpaRepository roleJpaRepository, UserMapper mapper, BlindIndexService blindIndexService) {
        this.jpaRepository = jpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.mapper = mapper;
        this.blindIndexService = blindIndexService;
    }

    @Override
    public User save(User user) {
        Set<RoleJpaEntity> roles = user.getRoleIds().isEmpty() ? Set.of() : new HashSet<>(roleJpaRepository.findAllById(user.getRoleIds()));
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
        return jpaRepository.findByEmailBlindIndex(blindIndexService.hmac(EMAIL_FIELD_CONTEXT, email)).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmailBlindIndex(blindIndexService.hmac(EMAIL_FIELD_CONTEXT, email));
    }

    @Override
    public boolean existsByUsernameAndIdNot(String username, UUID userId) {
        return jpaRepository.existsByUsernameAndIdNot(username, userId);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, UUID userId) {
        return jpaRepository.existsByEmailBlindIndexAndIdNot(blindIndexService.hmac(EMAIL_FIELD_CONTEXT, email), userId);
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
                // email is ciphertext (non-deterministic, random IV) — substring LIKE is impossible on
                // it, so the combined search box keeps username substring matching but the email side
                // becomes exact-match via the blind index (accepted trade-off for encrypting email).
                String likePattern = "%" + criteria.search().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), likePattern),
                        cb.equal(root.get("emailBlindIndex"), blindIndexService.hmac(EMAIL_FIELD_CONTEXT, criteria.search()))
                ));
            }

            assert query != null;
            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
