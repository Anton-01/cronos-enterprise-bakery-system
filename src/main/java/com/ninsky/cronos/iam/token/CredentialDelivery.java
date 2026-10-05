package com.ninsky.cronos.iam.token;

import java.time.Instant;
import java.util.UUID;

/**
 * Emails to send once the issuing transaction commits. Raw secrets live only in these in-memory
 * events; {@code toString} never prints them.
 */
public sealed interface CredentialDelivery {

    UUID userId();

    String email();

    String displayName();

    /** {@code es-MX} or {@code en}. */
    String locale();

    record Invitation(UUID userId, String email, String displayName, String locale, String rawToken, Instant expiresAt)
            implements CredentialDelivery {
        @Override
        public String toString() {
            return "Invitation[userId=" + userId + "]";
        }
    }

    record TemporaryPassword(UUID userId, String email, String displayName, String locale, String rawPassword)
            implements CredentialDelivery {
        @Override
        public String toString() {
            return "TemporaryPassword[userId=" + userId + "]";
        }
    }

    record PasswordResetLink(UUID userId, String email, String displayName, String locale, String rawToken, Instant expiresAt)
            implements CredentialDelivery {
        @Override
        public String toString() {
            return "PasswordResetLink[userId=" + userId + "]";
        }
    }

    /** Verification link to the new address. */
    record EmailVerification(UUID userId, String email, String displayName, String locale, String rawToken, Instant expiresAt)
            implements CredentialDelivery {
        @Override
        public String toString() {
            return "EmailVerification[userId=" + userId + "]";
        }
    }

    /** Notice to the old address that the account email changed. */
    record EmailChangedNotice(UUID userId, String email, String displayName, String locale, String newEmailMasked)
            implements CredentialDelivery {
    }
}
