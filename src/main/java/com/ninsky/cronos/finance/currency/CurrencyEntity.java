package com.ninsky.cronos.finance.currency;

import com.ninsky.cronos.finance.shared.FinanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** ISO 4217 catalog row (V10 {@code currencies}). Invariants needing the database live in the service. */
@Entity
@Table(name = "currencies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurrencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3)
    private String code;

    @Column(name = "numeric_code", nullable = false, length = 3)
    private String numericCode;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 5)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private Short decimalPlaces;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_position", nullable = false, length = 6)
    private SymbolPosition symbolPosition;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FinanceStatus status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static CurrencyEntity create(CurrencyDraft draft, UUID actor, Instant now) {
        CurrencyEntity entity = new CurrencyEntity();
        entity.apply(draft);
        entity.status = FinanceStatus.ACTIVE;
        entity.isDefault = false;
        entity.createdAt = now;
        entity.createdBy = actor;
        return entity;
    }

    public void update(CurrencyDraft draft, UUID actor, Instant now) {
        apply(draft);
        touch(actor, now);
    }

    public void changeStatus(FinanceStatus newStatus, UUID actor, Instant now) {
        if (isDefault && newStatus != FinanceStatus.ACTIVE) {
            throw new IllegalStateException("The default currency must stay ACTIVE");
        }
        status = newStatus;
        touch(actor, now);
    }

    public void markDefault(boolean value, UUID actor, Instant now) {
        if (value && status != FinanceStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE currency can be the default");
        }
        isDefault = value;
        touch(actor, now);
    }

    public boolean isActive() {
        return status == FinanceStatus.ACTIVE;
    }

    public CurrencyDraft draft() {
        return new CurrencyDraft(code, numericCode, name, symbol, decimalPlaces, symbolPosition);
    }

    private void apply(CurrencyDraft draft) {
        code = draft.code();
        numericCode = draft.numericCode();
        name = draft.name();
        symbol = draft.symbol();
        decimalPlaces = (short) draft.decimalPlaces();
        symbolPosition = draft.symbolPosition();
    }

    private void touch(UUID actor, Instant now) {
        updatedAt = now;
        updatedBy = actor;
    }
}
