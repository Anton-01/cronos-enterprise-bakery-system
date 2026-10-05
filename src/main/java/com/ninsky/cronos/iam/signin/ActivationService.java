package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.PasswordPolicy;
import com.ninsky.cronos.iam.policy.PasswordRules;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.user.UserStatusWriter;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Invitation acceptance ({@code POST /auth/activate}, spec §3.3/§3.5): the user sets a password that
 * satisfies the policy and moves PENDING_ACTIVATION → ACTIVE. A failed attempt rolls back, so the
 * token stays usable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationService {

    private final UserTokens tokens;
    private final UserRepositoryPort users;
    private final AccountStandings standings;
    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyProvider policies;
    private final CredentialWriter credentials;
    private final UserStatusWriter statusWriter;
    private final UserDirectory directory;
    private final AuditRecorder recorder;

    @Transactional
    public void activate(String token, String password) {
        Violations violations = new Violations()
                .invalidIf(token == null || token.isBlank(), "token", "api.validation.required")
                .invalidIf(password == null || password.isEmpty(), "password", "api.validation.required");
        violations.throwIfAny();

        UUID userId = tokens.consume(token, TokenPurpose.INVITATION)
                .filter(id -> standings.find(id).map(s -> s.status() == UserStatus.PENDING_ACTIVATION).orElse(false))
                .orElseThrow(() -> ApiException.invalid("token", "security.token.invalid"));
        User user = users.findById(userId).orElseThrow(() -> ApiException.invalid("token", "security.token.invalid"));

        List<String> broken = passwordPolicy.violations(password, user.getUsername(), user.getEmail(), userId);
        PasswordRules.toViolations(new Violations(), "password", broken, policies.current()).throwIfAny();

        credentials.setPassword(userId, password, true);
        statusWriter.apply(userId, new UserStatusWriter.Change(UserStatus.ACTIVE, null, null, null, null), null);
        recorder.record(AuditEvent.of(AuditAction.INVITATION_ACCEPTED, AuditTargets.USER, userId,
                directory.ref(userId).map(UserRef::displayName).orElse(user.getUsername())).build());
        log.info("Invitation accepted by user {}", userId);
    }
}
