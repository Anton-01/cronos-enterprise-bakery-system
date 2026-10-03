package com.ninsky.cronos.account.fiscal.infrastructure;

import com.ninsky.cronos.account.fiscal.domain.TaxpayerType;
import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** {@code user_fiscal_data}, keyed by the owning user's id (1:1, cascade-deleted with the user). */
@Entity
@Table(name = "user_fiscal_data")
@Getter
@Setter
@NoArgsConstructor
public class UserFiscalDataJpaEntity extends AuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "legal_name", nullable = false, length = 254)
    private String legalName;

    @Column(name = "tax_id", nullable = false, length = 13)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "taxpayer_type", nullable = false, length = 12)
    private TaxpayerType taxpayerType;

    @Column(name = "tax_regime", nullable = false, length = 3)
    private String taxRegime;

    @Embedded
    private FiscalAddressEmbeddable address;

    /** Null until persisted — Spring Data's isNew() check for an assigned-id entity. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UserFiscalDataJpaEntity(UUID userId) {
        this.userId = userId;
    }
}
