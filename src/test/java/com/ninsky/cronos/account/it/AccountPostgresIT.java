package com.ninsky.cronos.account.it;

import com.ninsky.cronos.account.avatar.infrastructure.AvatarObjectCleanupListener;
import com.ninsky.cronos.account.avatar.infrastructure.AvatarUrlMapping;
import com.ninsky.cronos.account.avatar.infrastructure.InMemoryAvatarStorage;
import com.ninsky.cronos.account.fiscal.infrastructure.FiscalDataMapperImpl;
import com.ninsky.cronos.account.fiscal.infrastructure.JpaFiscalDataRepository;
import com.ninsky.cronos.account.profile.infrastructure.JpaUserAccountRepository;
import com.ninsky.cronos.account.profile.infrastructure.UserProfileMapperImpl;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.account.shared.infrastructure.audit.AuditLogWriter;
import com.ninsky.cronos.account.shared.infrastructure.audit.SpringEventAuditTrail;
import com.ninsky.cronos.infrastructure.config.JpaAuditingConfig;
import com.ninsky.cronos.infrastructure.persistence.audit.adapter.AuditLogRepositoryAdapter;
import com.ninsky.cronos.infrastructure.persistence.audit.mapper.AuditLogMapper;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedLocalDateConverter;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedStringConverter;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * JPA slice against a real PostgreSQL running every Flyway migration, so {@code ddl-auto=validate}
 * also proves the new entities match V6. Tests are NOT wrapped in a rolled-back test transaction:
 * AFTER_COMMIT behaviour (audit rows, avatar cleanup) needs real commits, driven via
 * {@link #tx}. Field encryption is replaced by an identity mock — it is not what is under test.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, EncryptedStringConverter.class, EncryptedLocalDateConverter.class,
        JacksonAutoConfiguration.class,
        JpaFiscalDataRepository.class, FiscalDataMapperImpl.class,
        JpaUserAccountRepository.class, UserProfileMapperImpl.class, AvatarUrlMapping.class, AvatarObjectCleanupListener.class,
        SpringEventAuditTrail.class, AuditLogWriter.class, AuditLogRepositoryAdapter.class, AuditLogMapper.class,
        AccountPostgresIT.Beans.class})
abstract class AccountPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    protected FieldEncryptionService fieldEncryptionService;
    @MockitoBean
    protected RequestContextUtil requestContextUtil;

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected InMemoryAvatarStorage avatarStorage;
    @Autowired
    private PlatformTransactionManager transactionManager;

    protected TransactionTemplate tx;

    @TestConfiguration
    static class Beans {
        @Bean
        InMemoryAvatarStorage avatarStorage() {
            return new InMemoryAvatarStorage();
        }

        @Bean
        PiiMasker piiMasker() {
            return new PiiMasker();
        }
    }

    @BeforeEach
    void baseSetUp() {
        tx = new TransactionTemplate(transactionManager);
        when(fieldEncryptionService.encrypt(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fieldEncryptionService.decrypt(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requestContextUtil.getClientIp()).thenReturn("203.0.113.7");
        when(requestContextUtil.getUserAgent()).thenReturn("JUnit");
    }

    /** Inserts a minimal valid users row (plus an optional profile) straight through JDBC. */
    protected UUID insertUser(String username, String phoneNumber) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("""
                INSERT INTO users (id, username, email, email_blind_index, enabled, account_non_locked, account_non_expired,
                                   credentials_non_expired, email_verified, two_factor_enabled, failed_login_attempts,
                                   password_needs_change, created_at, version)
                VALUES (?, ?, ?, ?, true, true, true, true, false, false, 0, false, ?, 0)""",
                id, username, username + "@cronos.test", id.toString().substring(0, 32), now);
        if (phoneNumber != null) {
            jdbc.update("""
                    INSERT INTO user_profiles (id, user_id, phone_number, email_notifications, push_notifications,
                                               sms_notifications, created_at)
                    VALUES (?, ?, ?, true, true, false, ?)""", UUID.randomUUID(), id, phoneNumber, now);
        }
        return id;
    }
}
