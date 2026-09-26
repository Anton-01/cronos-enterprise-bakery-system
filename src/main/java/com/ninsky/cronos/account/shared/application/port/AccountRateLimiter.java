package com.ninsky.cronos.account.shared.application.port;

import java.util.UUID;

/** Per-user write throttling for account-settings mutations. */
public interface AccountRateLimiter {

    Decision tryConsume(UUID userId, RateLimitedAction action);

    enum RateLimitedAction {
        AVATAR_UPLOAD,
        PROFILE_WRITE,
        FISCAL_WRITE
    }

    record Decision(boolean allowed, long retryAfterSeconds) {
        public static Decision allow() {
            return new Decision(true, 0);
        }

        public static Decision deny(long retryAfterSeconds) {
            return new Decision(false, Math.max(1, retryAfterSeconds));
        }
    }
}
