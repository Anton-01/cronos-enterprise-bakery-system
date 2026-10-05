package com.ninsky.cronos.finance;

import com.ninsky.cronos.finance.currency.CurrencyDraft;
import com.ninsky.cronos.finance.currency.CurrencyEntity;
import com.ninsky.cronos.finance.currency.SymbolPosition;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.taxrate.TaxRateDraft;
import com.ninsky.cronos.finance.taxrate.TaxRateEntity;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.assertj.core.api.ThrowableAssert;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Builders for finance entities in unit tests (ids/versions are DB-assigned in production). */
public final class FinanceTestData {

    public static final UUID ACTOR_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    public static final Actor ACTOR = new Actor(ACTOR_ID, "admin", Set.of(), false);
    /** 2026-10-05 12:00 in Mexico City. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private FinanceTestData() {
    }

    public static CurrencyEntity currency(long id, String code, String numeric, int decimals, boolean isDefault, FinanceStatus status) {
        CurrencyEntity entity = CurrencyEntity.create(new CurrencyDraft(code, numeric, "Currency " + code, "$", decimals, SymbolPosition.BEFORE),
                ACTOR_ID, Instant.EPOCH);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "version", 3L);
        ReflectionTestUtils.setField(entity, "isDefault", isDefault);
        ReflectionTestUtils.setField(entity, "status", status);
        return entity;
    }

    public static TaxRateEntity taxRate(long id, String code, TaxFactorType factor, String rate, LocalDate from, LocalDate to,
                                        boolean isDefault, FinanceStatus status) {
        TaxRateEntity entity = TaxRateEntity.create(new TaxRateDraft(code, "Rate " + code, null, factor,
                rate == null ? null : new BigDecimal(rate), from, to), ACTOR_ID, Instant.EPOCH);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "version", 2L);
        ReflectionTestUtils.setField(entity, "isDefault", isDefault);
        ReflectionTestUtils.setField(entity, "status", status);
        return entity;
    }

    /** Asserts an {@link ApiException} whose violations carry exactly these codes and fields, in order. */
    public static void assertViolations(ThrowableAssert.ThrowingCallable call, Object... codeFieldPairs) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
            org.assertj.core.api.Assertions.assertThat(ex.violations())
                    .extracting(v -> v.code().name() + ":" + v.field())
                    .containsExactly(java.util.stream.IntStream.range(0, codeFieldPairs.length / 2)
                            .mapToObj(i -> ((ApiErrorCode) codeFieldPairs[2 * i]).name() + ":" + codeFieldPairs[2 * i + 1])
                            .toArray(String[]::new));
        });
    }
}
