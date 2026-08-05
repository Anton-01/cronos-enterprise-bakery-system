package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, UUID userId);

    boolean existsByEmailAndIdNot(String email, UUID userId);

    List<User> findExpiredLockedAccounts(LocalDateTime now);

    /** Dynamic admin search — {@link UserSearchCriteria} keeps JPA's {@code Specification} out of this port. */
    Page<User> search(UserSearchCriteria criteria, Pageable pageable);
}
