package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.infrastructure.config.security.SecurityProperties;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private final GoogleAuthenticator googleAuthenticator;
    private final SecurityProperties securityProperties;

    public String generateSecretKey() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        return key.getKey();
    }

    public String generateQRCodeUrl(User user, String secret) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthURL(
                securityProperties.getTwoFactor().getIssuer(),
                user.getEmail(),
                new GoogleAuthenticatorKey.Builder(secret).build()
        );
    }

    public boolean validateCode(String secret, int code) {
        return googleAuthenticator.authorize(secret, code);
    }

    public boolean isCodeValid(User user, int code) {
        if (user.getTwoFactorSecret() == null) {
            return false;
        }
        return validateCode(user.getTwoFactorSecret(), code);
    }
}
