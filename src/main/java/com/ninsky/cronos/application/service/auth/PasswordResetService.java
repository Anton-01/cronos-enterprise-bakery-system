package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.domain.entity.auth.PasswordResetToken;
import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.infrastructure.exception.InvalidTokenException;
import com.ninsky.cronos.infrastructure.persistence.auth.PasswordResetTokenRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public void processForgotPassword(String email) {
        log.info("Processing forgot password request for email: {}", email);

        userRepository.findByEmail(email).ifPresent(user -> {
            String tokenStr = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken(tokenStr, user, LocalDateTime.now().plusHours(1));

            tokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/auth/reset-password?token=" + tokenStr;

            EmailRequest emailRequest = EmailRequest.builder().to(user.getEmail())
                    .subject("Recuperación de Contraseña - Cronos Bakery")
                    .templateName("auth/password-reset")
                    .variables(Map.of(
                            "username", user.getUsername(),
                            "resetLink", resetLink
                    )).build();

            mailService.sendHtmlEmail(emailRequest);
            log.info("Password reset token generated and email dispatched for user: {}", email);
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
        User user = resetToken.getUser();
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
