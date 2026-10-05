package com.ninsky.cronos.finance.settings;

import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The tenant's single calculation-settings row (V10 {@code finance_settings}, id = 1). */
@Entity
@Table(name = "finance_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinanceSettingsEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false, length = 10)
    private FinanceRoundingMode roundingMode;

    @Version
    private Long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public void update(boolean includeTax, FinanceRoundingMode mode, UUID actor, Instant now) {
        pricesIncludeTax = includeTax;
        roundingMode = mode;
        updatedAt = now;
        updatedBy = actor;
    }
}
