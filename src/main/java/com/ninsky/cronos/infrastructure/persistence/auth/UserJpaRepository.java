package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID>, JpaSpecificationExecutor<UserJpaEntity> {

    Optional<UserJpaEntity> findByUsername(String username);

    /**
     * {@code email} is non-deterministic ciphertext (random IV) — lookups go through the
     * deterministic {@code emailBlindIndex} column instead. See {@code UserRepositoryAdapter},
     * which computes the blind index from the plaintext argument before calling these.
     */
    Optional<UserJpaEntity> findByEmailBlindIndex(String emailBlindIndex);
    boolean existsByUsername(String username);
    boolean existsByEmailBlindIndex(String emailBlindIndex);

    @Query("SELECT COUNT(u) > 0 FROM UserJpaEntity u WHERE u.username = :username AND u.id != :userId")
    boolean existsByUsernameAndIdNot(@Param("username") String username, @Param("userId") UUID userId);

    @Query("SELECT COUNT(u) > 0 FROM UserJpaEntity u WHERE u.emailBlindIndex = :emailBlindIndex AND u.id != :userId")
    boolean existsByEmailBlindIndexAndIdNot(@Param("emailBlindIndex") String emailBlindIndex, @Param("userId") UUID userId);

    @Query("SELECT u FROM UserJpaEntity u WHERE u.accountNonLocked = false AND u.lockedUntil < :now")
    List<UserJpaEntity> findExpiredLockedAccounts(@Param("now") LocalDateTime now);
}
