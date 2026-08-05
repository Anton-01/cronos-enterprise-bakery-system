package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.UserProfile;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepositoryPort {

    UserProfile save(UserProfile profile);

    Optional<UserProfile> findByUserId(UUID userId);
}
