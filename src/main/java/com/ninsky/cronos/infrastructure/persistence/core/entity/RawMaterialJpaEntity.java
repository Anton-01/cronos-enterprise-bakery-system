package com.ninsky.cronos.infrastructure.persistence.core.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder @Entity
@Table(name = "raw_materials")
public class RawMaterialJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String brand;

    @Column(length = 500)
    private String supplier;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_unit_id", nullable = false)
    private MeasurementUnitJpaEntity purchaseUnit;

    @Column(name = "purchase_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal purchaseQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, length = 3)
    private String currency = "MXN";

    @Column(name = "yield_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal yieldPercentage;

    @Column(name = "base_unit_cost", precision = 15, scale = 6)
    private BigDecimal baseUnitCost;

    @Column(name = "current_stock", precision = 15, scale = 4)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "minimum_stock", precision = 15, scale = 4)
    private BigDecimal minimumStock;

    @Column(name = "last_purchase_date")
    private LocalDateTime lastPurchaseDate;

    @Column(name = "last_price_update")
    private LocalDateTime lastPriceUpdate;

    @Column(name = "needs_recalculation", nullable = false)
    private boolean needsRecalculation = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "density", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal density = BigDecimal.ONE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "raw_material_allergens",
            joinColumns = @JoinColumn(name = "raw_material_id"),
            inverseJoinColumns = @JoinColumn(name = "allergen_id")
    )
    @Builder.Default
    private Set<AllergenJpaEntity> allergens = new HashSet<>();
}
