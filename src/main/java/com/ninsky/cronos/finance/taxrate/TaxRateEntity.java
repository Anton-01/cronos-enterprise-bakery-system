package com.ninsky.cronos.finance.taxrate;

import com.ninsky.cronos.finance.pricing.TaxFactorType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** IVA rate catalog row (V10 {@code tax_rates}). */
@Entity
@Table(name = "tax_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 250)
    private String description;

    @Column(name = "sat_tax_code", nullable = false, length = 3)
    private String satTaxCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_type", nullable = false, length = 6)
    private TaxFactorType factorType;

    @Column(name = "rate_percent", precision = 7, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

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

    public static TaxRateEntity create(TaxRateDraft draft, UUID actor, Instant now) {
        TaxRateEntity entity = new TaxRateEntity();
        entity.apply(draft);
        entity.satTaxCode = TaxRateRules.SAT_IVA;
        entity.status = FinanceStatus.ACTIVE;
        entity.isDefault = false;
        entity.createdAt = now;
        entity.createdBy = actor;
        return entity;
    }

    public void update(TaxRateDraft draft, UUID actor, Instant now) {
        apply(draft);
        touch(actor, now);
    }

    public void changeStatus(FinanceStatus newStatus, UUID actor, Instant now) {
        if (isDefault && newStatus != FinanceStatus.ACTIVE) {
            throw new IllegalStateException("The default tax rate must stay ACTIVE");
        }
        status = newStatus;
        touch(actor, now);
    }

    public void markDefault(boolean value, UUID actor, Instant now) {
        if (value && status != FinanceStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE tax rate can be the default");
        }
        isDefault = value;
        touch(actor, now);
    }

    public boolean isActive() {
        return status == FinanceStatus.ACTIVE;
    }

    public boolean isValidOn(LocalDate day) {
        return TaxRateRules.isValidOn(validFrom, validTo, day);
    }

    public TaxRateDraft draft() {
        return new TaxRateDraft(code, name, description, factorType, ratePercent, validFrom, validTo);
    }

    private void apply(TaxRateDraft draft) {
        code = draft.code();
        name = draft.name();
        description = draft.description();
        factorType = draft.factorType();
        ratePercent = draft.factorType() == TaxFactorType.EXENTO ? null : draft.ratePercent();
        validFrom = draft.validFrom();
        validTo = draft.validTo();
    }

    private void touch(UUID actor, Instant now) {
        updatedAt = now;
        updatedBy = actor;
    }
}
