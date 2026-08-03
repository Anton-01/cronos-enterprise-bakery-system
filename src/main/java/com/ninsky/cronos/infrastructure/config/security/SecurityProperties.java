package com.ninsky.cronos.infrastructure.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "security")
@Getter @Setter
public class SecurityProperties {

    private AccountLockout accountLockout = new AccountLockout();
    private RateLimit rateLimit = new RateLimit();
    private Password password = new Password();
    private TwoFactor twoFactor = new TwoFactor();

    @Getter @Setter
    public static class AccountLockout {
        private Integer maxAttempts = 5;
        private Integer lockoutDurationMinutes = 60;
        private Integer incrementFactor = 2;
        private Integer maxLockoutDurationHours = 24;
    }

    @Getter @Setter
    public static class RateLimit {
        private Endpoint login = new Endpoint(10, 10, 60);
        private Endpoint register = new Endpoint(5, 5, 300);
        private Endpoint refresh = new Endpoint(20, 20, 60);

        @Getter @Setter
        public static class Endpoint {
            private Long capacity;
            private Long refillTokens;
            private Long refillDurationSeconds;

            public Endpoint() {}

            public Endpoint(long capacity, long refillTokens, long refillDurationSeconds) {
                this.capacity = capacity;
                this.refillTokens = refillTokens;
                this.refillDurationSeconds = refillDurationSeconds;
            }
        }
    }

    @Getter @Setter
    public static class Password {
        private Integer minLength = 8;
        private Integer maxLength = 128;
        private Boolean requireUppercase = true;
        private Boolean requireLowercase = true;
        private Boolean requireDigit = true;
        private Boolean requireSpecialChar = true;
        private Integer historyCount = 5;
    }

    @Getter @Setter
    public static class TwoFactor {
        private String issuer = "Enterprise Auth Service";
        private Integer qrCodeWidth = 250;
        private Integer qrCodeHeight = 250;
        private Integer windowSize = 3;
    }
}
