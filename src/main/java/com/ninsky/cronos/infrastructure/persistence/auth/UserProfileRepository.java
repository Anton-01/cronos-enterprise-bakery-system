package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.domain.entity.auth.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    // Busca directamente el perfil cruzando con la llave de la tabla de autenticación
    Optional<UserProfile> findByUserId(UUID userId);
}
