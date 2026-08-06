package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.application.event.PasswordResetRequestedEvent;
import com.ninsky.cronos.domain.model.auth.PasswordResetToken;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.port.auth.PasswordResetTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.InvalidTokenException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void processForgotPassword(String email) {
        log.info("Processing forgot password request for email: {}", email);

        userRepository.findByEmail(email).ifPresent(user -> {
            String tokenStr = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(tokenStr).userId(user.getId())
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(false).createdAt(LocalDateTime.now())
                    .build();

            tokenRepository.save(resetToken);

            eventPublisher.publishEvent(PasswordResetRequestedEvent.builder()
                    .userId(user.getId()).resetToken(tokenStr).requestedByAdmin(false).build());
            log.info("Password reset token generated and email dispatch requested for user: {}", email);
        });
    }

    @Transactional
    public void resetPasswordWithToken(String tokenStr, String newPassword) {
        log.info("Attempting to reset password with provided token");

        PasswordResetToken resetToken = tokenRepository.findByToken(tokenStr).orElseThrow(() -> new InvalidTokenException("El token es inválido o no existe."));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("Este token ya fue utilizado.");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("El token ha expirado. Solicita uno nuevo.");
        }

        // Update password user
        User user = userRepository.findById(resetToken.getUserId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());

        // Unlock the account if it has been locked due to a brute-force attack
        user.setAccountNonLocked(true);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        // Burn the token so it can't be used again
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Password successfully reset for user ID: {}", user.getId());
    }
}
