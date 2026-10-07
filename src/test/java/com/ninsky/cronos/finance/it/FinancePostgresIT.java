package com.ninsky.cronos.finance.it;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.finance.currency.CurrencyQueryCustomRepository;
import com.ninsky.cronos.finance.currency.CurrencyRequest;
import com.ninsky.cronos.finance.currency.CurrencyResponse;
import com.ninsky.cronos.finance.currency.CurrencyService;
import com.ninsky.cronos.finance.currency.SymbolPosition;
import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import com.ninsky.cronos.finance.pricing.PricingSnapshot;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.settings.FinanceSettingsRequest;
import com.ninsky.cronos.finance.settings.FinanceSettingsResponse;
import com.ninsky.cronos.finance.settings.FinanceSettingsService;
import com.ninsky.cronos.finance.settings.PricingSnapshotResolver;
import com.ninsky.cronos.finance.settings.PricingSnapshotResolver.PricingInput;
import com.ninsky.cronos.finance.shared.FinanceLocks;
import com.ninsky.cronos.finance.shared.FinanceSettingsCache;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.StatusRequest;
import com.ninsky.cronos.finance.shared.UserRefCustomRepository;
import com.ninsky.cronos.finance.shared.VersionRequest;
import com.ninsky.cronos.finance.taxrate.DefaultTaxRateExpiryJob;
import com.ninsky.cronos.finance.taxrate.TaxRateOption;
import com.ninsky.cronos.finance.taxrate.TaxRateQueryCustomRepository;
import com.ninsky.cronos.finance.taxrate.TaxRateRequest;
import com.ninsky.cronos.finance.taxrate.TaxRateService;
import com.ninsky.cronos.iam.audit.JdbcAuditRecorder;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedLocalDateConverter;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedStringConverter;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Finance against a real PostgreSQL running every migration: entity mappings ({@code ddl-auto=validate}),
 * EXISTS-based {@code inUse}, the single-default partial index under concurrency, audit rows written
 * in the business transaction and the idempotent expiry job. Tests share one database, in order.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({CurrencyService.class, CurrencyQueryCustomRepository.class, TaxRateService.class, TaxRateQueryCustomRepository.class, FinanceSettingsService.class,
        PricingSnapshotResolver.class, FinanceLocks.class, FinanceSettingsCache.class, UserRefCustomRepository.class, DefaultTaxRateExpiryJob.class,
        JdbcAuditRecorder.class, CacheConfig.class, EncryptedStringConverter.class, EncryptedLocalDateConverter.class, FinancePostgresIT.Beans.class})
class FinancePostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 2026-10-05 12:00 in Mexico City. */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @MockitoBean
    private ActorProvider actors;
    @MockitoBean
    private RequestContextUtil requestContext;
    @MockitoBean
    private AvatarStorage avatarStorage;
    @MockitoBean
    private FieldEncryptionService fieldEncryption;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CurrencyService currencies;
    @Autowired
    private TaxRateService taxRates;
    @Autowired
    private FinanceSettingsService settings;
    @Autowired
    private PricingSnapshotResolver snapshots;
    @Autowired
    private DefaultTaxRateExpiryJob expiryJob;

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    @BeforeEach
    void setUp() {
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Long.class, ACTOR_ID) == 0) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbc.update("""
                    INSERT INTO users (id, username, email, email_blind_index, enabled, account_non_locked, account_non_expired,
                                       credentials_non_expired, email_verified, two_factor_enabled, failed_login_attempts,
                                       password_needs_change, created_at, version)
                    VALUES (?, 'aortiz', 'aortiz@cronos.test', ?, true, true, true, true, false, false, 0, false, ?, 0)""",
                    ACTOR_ID, ACTOR_ID.toString().substring(0, 32), now);
            jdbc.update("""
                    INSERT INTO user_profiles (id, user_id, first_name, last_name, email_notifications, push_notifications,
                                               sms_notifications, created_at)
                    VALUES (?, ?, 'Antonio', 'Ortiz', true, true, false, ?)""", UUID.randomUUID(), ACTOR_ID, now);
        }
        Actor actor = new Actor(ACTOR_ID, "aortiz", Set.of(), false);
        when(actors.require()).thenReturn(actor);
        when(actors.current()).thenReturn(Optional.of(actor));
        when(avatarStorage.publicUrl(any())).thenReturn(URI.create("https://cdn.test/avatar.jpg"));
        when(fieldEncryption.encrypt(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fieldEncryption.decrypt(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @Order(1)
    void seedsAreReadThroughTheEntities() {
        FinanceSettingsResponse current = settings.current();

        assertThat(current.defaultCurrency().code()).isEqualTo("MXN");
        assertThat(current.defaultTaxRate().code()).isEqualTo("IVA_16");
        assertThat(current.defaultTaxRate().ratePercent()).isEqualByComparingTo("16");
        assertThat(current.roundingMode()).isEqualTo(FinanceRoundingMode.HALF_UP);
        assertThat(currencies.catalog().getFirst().code()).isEqualTo("MXN");
        assertThat(currencies.page(null, null, 0, 100, null).content()).extracting(CurrencyResponse::code).containsExactly("EUR", "MXN", "USD");
    }

    @Test
    @Order(2)
    void currencyLifecycleIsAuditedInTheSameTransaction() {
        CurrencyResponse yen = currencies.create(new CurrencyRequest("JPY", "392", "Yen japonés", "¥", 0, SymbolPosition.BEFORE, null));
        CurrencyResponse renamed = currencies.update(yen.id(), new CurrencyRequest("JPY", "392", "Yen", "¥", 0, SymbolPosition.BEFORE,
                yen.version()));

        assertThat(renamed.version()).isEqualTo(yen.version() + 1);
        assertThat(renamed.updatedBy().displayName()).isEqualTo("Antonio Ortiz");
        assertThat(renamed.updatedBy().avatarUrl()).isNull();
        assertThat(currencies.page("ye", FinanceStatus.ACTIVE, 0, 10, "name,asc").content()).extracting(CurrencyResponse::code)
                .containsExactly("JPY");
        assertThatThrownBy(() -> currencies.create(new CurrencyRequest("JPY", "392", "YEN", "¥", 0, SymbolPosition.BEFORE, null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.violations())
                        .extracting(v -> v.field()).containsExactly("code", "numericCode", "name"));

        currencies.delete(yen.id());

        assertThat(auditActions("CURRENCY", yen.id())).containsExactly("CURRENCY_CREATED", "CURRENCY_UPDATED", "CURRENCY_DELETED");
    }

    @Test
    @Order(3)
    void documentsMakeCurrenciesAndRatesInUse() {
        long ivaZero = jdbc.queryForObject("SELECT id FROM tax_rates WHERE code = 'IVA_0'", Long.class);
        insertQuote("usd", ivaZero);
        CurrencyResponse usd = currencyByCode("USD");

        assertThat(usd.inUse()).isTrue();
        assertThatThrownBy(() -> currencies.update(usd.id(), new CurrencyRequest("USD", "840", usd.name(), "$", 0, SymbolPosition.BEFORE,
                usd.version())))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.violations())
                        .extracting(v -> v.code() + ":" + v.field()).containsExactly("RESOURCE_IN_USE:decimalPlaces"));
        assertThatThrownBy(() -> currencies.delete(usd.id())).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.primaryCode()).isEqualTo(ApiErrorCode.RESOURCE_IN_USE));
        assertThatThrownBy(() -> taxRates.update(ivaZero, new TaxRateRequest("IVA_0", "IVA tasa 0%", null, TaxFactorType.TASA,
                BigDecimal.ONE, LocalDate.of(2010, 1, 1), null, 0L)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.violations())
                        .extracting(v -> v.code() + ":" + v.field()).containsExactly("RESOURCE_IN_USE:ratePercent"));
    }

    @Test
    @Order(4)
    void catalogExcludesExpiredAndScheduledRates() {
        taxRates.create(new TaxRateRequest("IVA_OLD", "Vencida", null, TaxFactorType.TASA, BigDecimal.TEN, LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 10, 4), null));
        taxRates.create(new TaxRateRequest("IVA_NEXT", "Programada", null, TaxFactorType.TASA, BigDecimal.TEN, LocalDate.of(2026, 10, 6),
                null, null));

        assertThat(taxRates.catalog()).extracting(TaxRateOption::code)
                .startsWith("IVA_16")
                .contains("IVA_8_FRONTERA", "IVA_0", "IVA_EXENTO")
                .doesNotContain("IVA_OLD", "IVA_NEXT");
    }

    @Test
    @Order(5)
    void concurrentDefaultSwitchesLeaveExactlyOneDefault() throws Exception {
        CurrencyResponse usd = currencyByCode("USD");
        CurrencyResponse eur = currencyByCode("EUR");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<CompletableFuture<CurrencyResponse>> calls = List.of(usd, eur).stream()
                    .map(target -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return currencies.makeDefault(target.id(), new VersionRequest(target.version()));
                    }, pool))
                    .toList();
            start.countDown();
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM currencies WHERE is_default", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT code FROM currencies WHERE is_default", String.class)).isIn("USD", "EUR");
        assertThatThrownBy(() -> currencies.changeStatus(currencyByCode(settings.current().defaultCurrency().code()).id(),
                new StatusRequest(FinanceStatus.INACTIVE, currencyByCode(settings.current().defaultCurrency().code()).version())))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.primaryCode()).isEqualTo(ApiErrorCode.DEFAULT_LOCKED));
    }

    @Test
    @Order(6)
    void existingDocumentsKeepTheirSnapshotWhenDefaultsChange() {
        PricingSnapshot stored = new PricingSnapshot("MXN", 2, 1L, TaxFactorType.TASA, new BigDecimal("16.0000"), false,
                FinanceRoundingMode.HALF_UP);
        FinanceSettingsResponse before = settings.current();
        settings.update(new FinanceSettingsRequest(true, FinanceRoundingMode.HALF_EVEN, before.version()));

        assertThat(snapshots.forExistingDocument(stored, new PricingInput(null, null, null))).isEqualTo(stored);
        PricingSnapshot fresh = snapshots.forNewDocument(new PricingInput(null, null, null));
        assertThat(fresh.pricesIncludeTax()).isTrue();
        assertThat(fresh.roundingMode()).isEqualTo(FinanceRoundingMode.HALF_EVEN);
        assertThat(fresh.currencyCode()).isNotEqualTo("MXN");
        assertThatThrownBy(() -> settings.update(new FinanceSettingsRequest(false, FinanceRoundingMode.UP, before.version())))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.primaryCode()).isEqualTo(ApiErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    @Order(7)
    void expiredDefaultIsReportedOncePerDay() {
        long iva16 = jdbc.queryForObject("SELECT id FROM tax_rates WHERE is_default", Long.class);
        jdbc.update("UPDATE tax_rates SET valid_to = DATE '2026-01-31' WHERE id = ?", iva16);

        expiryJob.run();
        expiryJob.run();

        assertThat(auditActions("TAX_RATE", iva16)).containsOnlyOnce("FINANCE_DEFAULT_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT is_default FROM tax_rates WHERE id = ?", Boolean.class, iva16)).isTrue();
    }

    private CurrencyResponse currencyByCode(String code) {
        return currencies.page(code, null, 0, 10, null).content().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    private List<String> auditActions(String targetType, long id) {
        return jdbc.queryForList("SELECT action FROM audit_log WHERE target_type = ? AND target_id = ? ORDER BY id", String.class,
                targetType, Long.toString(id));
    }

    private void insertQuote(String currency, long taxRateId) {
        jdbc.update("""
                INSERT INTO quotes (id, quote_number, user_id, client_name, status, currency, subtotal, tax_rate, tax_amount, total,
                                    delivery_fee, extra_fee, is_revoked, views_count, created_at, version, currency_decimal_places,
                                    tax_rate_id, tax_factor_type, prices_include_tax, rounding_mode)
                VALUES (?, ?, ?, 'Cliente', 'DRAFT', ?, 100, 0, 0, 100, 0, 0, false, 0, now(), 0, 2, ?, 'TASA', false, 'HALF_UP')""",
                UUID.randomUUID(), "CR-" + UUID.randomUUID(), ACTOR_ID, currency, taxRateId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
