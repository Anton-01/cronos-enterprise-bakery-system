package com.ninsky.cronos.iam.it;

import com.ninsky.cronos.account.profile.infrastructure.migration.V7__normalize_legacy_phone_numbers;
import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.audit.JdbcAuditRecorder;
import com.ninsky.cronos.iam.policy.CachedSecurityPolicyProvider;
import com.ninsky.cronos.iam.policy.JdbcTwoFactorRequirement;
import com.ninsky.cronos.iam.policy.SecurityPolicyStore;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.migration.V11__iam_seed;
import com.ninsky.cronos.iam.shared.migration.V14__remove_super_admin_from_2fa_required_roles;
import com.ninsky.cronos.iam.shared.migration.V16__move_totp_secrets;
import com.ninsky.cronos.iam.twofactor.OtherSessions;
import com.ninsky.cronos.iam.twofactor.QrCodeRenderer;
import com.ninsky.cronos.iam.twofactor.SecondFactor;
import com.ninsky.cronos.iam.twofactor.Totp;
import com.ninsky.cronos.iam.twofactor.TwoFactorAccountService;
import com.ninsky.cronos.iam.twofactor.TwoFactorSecretCipher;
import com.ninsky.cronos.iam.twofactor.TwoFactorStore;
import com.ninsky.cronos.iam.twofactor.Base32;
import com.ninsky.cronos.iam.twofactor.api.ConfirmEnrollmentRequest;
import com.ninsky.cronos.iam.twofactor.api.DisableTwoFactorRequest;
import com.ninsky.cronos.iam.twofactor.api.TwoFactorEnrollment;
import com.ninsky.cronos.iam.twofactor.api.TwoFactorRecoveryCodes;
import com.ninsky.cronos.iam.user.UserReadRepository;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.infrastructure.config.security.SecurityProperties;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Contract §8 against a real PostgreSQL running every migration: the SUPER_ADMIN break-glass data fix,
 * the gate's DB-backed requirement, enrolment end to end (one transaction, hashes only, audit, access
 * version), step reuse, recovery-code consumption and concurrent confirmations.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TwoFactorAccountService.class, TwoFactorStore.class, SecondFactor.class, TwoFactorSecretCipher.class,
        JdbcTwoFactorRequirement.class, CachedSecurityPolicyProvider.class, SecurityPolicyStore.class, UserReadRepository.class,
        AccessVersions.class, JdbcAuditRecorder.class, CacheConfig.class, V7__normalize_legacy_phone_numbers.class,
        V11__iam_seed.class, V14__remove_super_admin_from_2fa_required_roles.class, V16__move_totp_secrets.class,
        TwoFactorPostgresIT.Beans.class})
class TwoFactorPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PASSWORD = "Correct-Horse-9";

    @MockitoBean
    private ActorProvider actors;
    @MockitoBean
    private RequestContextUtil requestContext;
    @MockitoBean
    private FieldEncryptionService fieldEncryption;
    @MockitoBean
    private BlindIndexService blindIndex;
    @MockitoBean
    private OtherSessions otherSessions;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TwoFactorAccountService twoFactor;
    @Autowired
    private JdbcTwoFactorRequirement requirement;

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        QrCodeRenderer qrCodeRenderer() {
            return content -> "data:image/png;base64,QR";
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties();
        }
    }

    @BeforeEach
    void setUp() {
        when(fieldEncryption.encrypt(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fieldEncryption.decrypt(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private UUID user(String roleCode) {
        UUID id = UUID.randomUUID();
        String username = "u" + id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO users (id, username, email, email_blind_index, password, enabled, account_non_locked, account_non_expired,
                                   credentials_non_expired, email_verified, two_factor_enabled, failed_login_attempts,
                                   password_needs_change, created_at, version)
                VALUES (?, ?, ?, ?, ?, true, true, true, true, true, false, 0, false, ?, 0)""",
                id, username, username + "@cronos.test", id.toString().substring(0, 32),
                new BCryptPasswordEncoder(4).encode(PASSWORD), Timestamp.valueOf(LocalDateTime.now()));
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, roleCode);
        Actor actor = new Actor(id, username, Set.of(), false);
        when(actors.require()).thenReturn(actor);
        when(actors.current()).thenReturn(Optional.of(actor));
        return id;
    }

    private static String currentCode(TwoFactorEnrollment enrollment) {
        return Totp.code(Base32.decode(enrollment.secret()), Totp.step(Instant.now()));
    }

    private static ApiErrorCode codeOf(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e.primaryCode();
        }
        throw new AssertionError("expected an ApiException");
    }

    @Test
    void superAdminIsNeverTwoFactorMandatory() {
        assertThat(jdbc.queryForList("SELECT r.code FROM security_policy_2fa_roles p JOIN roles r ON r.id = p.role_id", String.class))
                .containsExactly("ADMIN");
        assertThat(jdbc.queryForObject("SELECT version FROM security_policy", Long.class)).isPositive();
        assertThat(jdbc.queryForList("SELECT reason FROM audit_log WHERE action = 'SECURITY_POLICY_UPDATED' AND actor_label = 'System migration'",
                String.class)).singleElement().asString().contains("break-glass");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'users'", String.class))
                .doesNotContain("two_factor_secret");

        UUID root = user("SUPER_ADMIN");
        jdbc.update("INSERT INTO security_policy_2fa_roles (role_id) SELECT id FROM roles WHERE code = 'SUPER_ADMIN'");
        try {
            assertThat(requirement.isRequired(root)).isFalse();
        } finally {
            jdbc.update("DELETE FROM security_policy_2fa_roles WHERE role_id = (SELECT id FROM roles WHERE code = 'SUPER_ADMIN')");
        }
    }

    @Test
    void adminEnrolsInOneTransactionAndPassesTheGate() {
        UUID admin = user("ADMIN");
        assertThat(requirement.mustEnrol(admin)).isTrue();
        long accessVersion = jdbc.queryForObject("SELECT access_version FROM users WHERE id = ?", Long.class, admin);

        TwoFactorEnrollment enrollment = twoFactor.startEnrollment(admin);
        TwoFactorRecoveryCodes result = twoFactor.confirm(admin, new ConfirmEnrollmentRequest(enrollment.enrollmentId(),
                currentCode(enrollment)));

        assertThat(result.recoveryCodes()).hasSize(10);
        assertThat(result.status().enabled()).isTrue();
        assertThat(result.status().required()).isTrue();
        assertThat(result.status().recoveryCodesRemaining()).isEqualTo(10);
        assertThat(requirement.mustEnrol(admin)).isFalse();
        assertThat(jdbc.queryForObject("SELECT two_factor_enabled FROM users WHERE id = ?", Boolean.class, admin)).isTrue();
        assertThat(jdbc.queryForObject("SELECT access_version FROM users WHERE id = ?", Long.class, admin)).isGreaterThan(accessVersion);
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM two_factor_enrollments WHERE id = ?", Boolean.class,
                enrollment.enrollmentId())).isTrue();
        List<String> hashes = jdbc.queryForList("SELECT code_hash FROM user_recovery_codes WHERE user_id = ?", String.class, admin);
        assertThat(hashes).hasSize(10).allMatch(hash -> hash.startsWith("$2")).doesNotContainAnyElementsOf(result.recoveryCodes());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'USER_2FA_ENABLED' AND target_id = ?",
                Long.class, admin.toString())).isOne();

        // The code that confirmed enrolment cannot be replayed at sign-in.
        assertThat(twoFactor.verifySignIn(admin, currentCode(enrollment)).accepted()).isFalse();
        // A recovery code works once.
        String recovery = result.recoveryCodes().getFirst();
        assertThat(twoFactor.verifySignIn(admin, recovery)).isEqualTo(SecondFactor.Result.RECOVERY_CODE);
        assertThat(twoFactor.verifySignIn(admin, recovery)).isEqualTo(SecondFactor.Result.INVALID_RECOVERY_CODE);
        assertThat(twoFactor.status(admin).recoveryCodesRemaining()).isEqualTo(9);
        // Mandatory 2FA stays on.
        assertThat(codeOf(() -> twoFactor.disable(admin, new DisableTwoFactorRequest(PASSWORD, result.recoveryCodes().get(1)))))
                .isEqualTo(ApiErrorCode.TWO_FACTOR_REQUIRED_BY_ROLE);
    }

    @Test
    void wrongCodesAreCountedEvenThoughTheRequestFails() {
        UUID user = user("USER");
        TwoFactorEnrollment enrollment = twoFactor.startEnrollment(user);
        String wrong = Totp.code(Base32.decode(enrollment.secret()), Totp.step(Instant.now()) + 5);
        for (int i = 0; i < 5; i++) {
            assertThat(codeOf(() -> twoFactor.confirm(user, new ConfirmEnrollmentRequest(enrollment.enrollmentId(), wrong))))
                    .isEqualTo(ApiErrorCode.INVALID_TOTP_CODE);
        }
        assertThat(codeOf(() -> twoFactor.confirm(user, new ConfirmEnrollmentRequest(enrollment.enrollmentId(), currentCode(enrollment)))))
                .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT failed_tries FROM two_factor_enrollments WHERE id = ?", Integer.class,
                enrollment.enrollmentId())).isEqualTo(5);
    }

    @Test
    void aNewEnrolmentInvalidatesThePreviousOne() {
        UUID user = user("USER");
        TwoFactorEnrollment first = twoFactor.startEnrollment(user);
        twoFactor.startEnrollment(user);
        assertThat(codeOf(() -> twoFactor.confirm(user, new ConfirmEnrollmentRequest(first.enrollmentId(), currentCode(first)))))
                .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void concurrentConfirmationsEnableOnce() throws Exception {
        UUID user = user("USER");
        TwoFactorEnrollment enrollment = twoFactor.startEnrollment(user);
        ConfirmEnrollmentRequest request = new ConfirmEnrollmentRequest(enrollment.enrollmentId(), currentCode(enrollment));
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Boolean>> attempts = List.of(1, 2).stream().map(i -> CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                    twoFactor.confirm(user, request);
                    return true;
                } catch (ApiException e) {
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }, pool)).toList();
            start.countDown();
            assertThat(attempts.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count()).isOne();
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_recovery_codes WHERE user_id = ?", Long.class, user)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'USER_2FA_ENABLED' AND target_id = ?",
                Long.class, user.toString())).isOne();
    }

    @Test
    void optionalTwoFactorCanBeTurnedOff() {
        UUID user = user("USER");
        TwoFactorEnrollment enrollment = twoFactor.startEnrollment(user);
        TwoFactorRecoveryCodes codes = twoFactor.confirm(user, new ConfirmEnrollmentRequest(enrollment.enrollmentId(),
                currentCode(enrollment)));
        assertThat(codeOf(() -> twoFactor.disable(user, new DisableTwoFactorRequest("wrong", codes.recoveryCodes().getFirst()))))
                .isEqualTo(ApiErrorCode.INVALID_PASSWORD);

        assertThat(twoFactor.disable(user, new DisableTwoFactorRequest(PASSWORD, codes.recoveryCodes().getFirst())).enabled()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_two_factor WHERE user_id = ?", Long.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_recovery_codes WHERE user_id = ?", Long.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'USER_2FA_DISABLED' AND target_id = ?",
                Long.class, user.toString())).isOne();
    }
}
