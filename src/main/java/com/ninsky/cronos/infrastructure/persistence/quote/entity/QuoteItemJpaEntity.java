package com.ninsky.cronos.infrastructure.persistence.quote.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code recipe_version_id}/{@code profit_margin_id} columns intentionally unmapped here (confirmed
 * dead — see the domain model javadoc); left in the DB as harmless orphaned columns, since
 * {@code ddl-auto: validate} only checks mapped columns exist, never fails on unmapped extras.
 */
@Entity
@Table(name = "quote_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteItemJpaEntity extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private QuoteJpaEntity quote;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_description", length = 1000)
    private String productDescription;

    @Column(name = "product_size", length = 100)
    private String productSize;

    @Column(name = "image_file_path", length = 500)
    private String imageFilePath;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "scale_factor", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal scaleFactor = BigDecimal.ONE;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "profit_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal profitPercentage;

    @Column(name = "unit_price", nullable = false, precision = 17, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 17, scale = 4)
    private BigDecimal subtotal;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "display_order")
    private Integer displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipe_configuration", columnDefinition = "jsonb")
    private String recipeConfiguration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergens", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private String allergens = "[]";

    @Column(name = "recipe_version")
    private Long recipeVersion;

    @Column(name = "cost_calculated_at")
    private Instant costCalculatedAt;

    @Column(name = "price_review_required", nullable = false)
    private boolean priceReviewRequired;

    @Version
    @Builder.Default
    private Long version = 0L;
}
