package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenRepositoryPort {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByToken(String token);
}
