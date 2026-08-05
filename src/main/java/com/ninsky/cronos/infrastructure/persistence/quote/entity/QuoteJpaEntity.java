package com.ninsky.cronos.infrastructure.persistence.quote.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.infrastructure.persistence.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteJpaEntity extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "quote_number", nullable = false, length = 100, unique = true)
    private String quoteNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_name", nullable = false, columnDefinition = "TEXT")
    private String clientName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_email", columnDefinition = "TEXT")
    private String clientEmail;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_phone", columnDefinition = "TEXT")
    private String clientPhone;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_address", columnDefinition = "TEXT")
    private String clientAddress;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MXN";

    @Column(name = "public_token", length = 100, unique = true)
    private String publicToken;

    @Column(name = "views_count", nullable = false)
    @Builder.Default
    private Integer viewsCount = 0;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean isRevoked = false;

    @Version
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuoteItemJpaEntity> items = new ArrayList<>();

    @Column(name = "delivery_fee", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "extra_fee", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal extraFee = BigDecimal.ZERO;

    @Column(name = "extra_fee_description", length = 200)
    private String extraFeeDescription;
}
