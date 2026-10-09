package com.ninsky.cronos.infrastructure.persistence.recipe.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity @Getter @Setter
@NoArgsConstructor
@AllArgsConstructor @Builder
@Table(name = "user_fixed_costs") @ToString
public class UserFixedCostJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "default_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal defaultAmount;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal percentage = BigDecimal.ZERO;

    @Column(name = "calculation_method", nullable = false, length = 50)
    private String calculationMethod;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "applies_by_default", nullable = false)
    @Builder.Default
    private boolean appliesByDefault = false;

    @Column(name = "monthly_amount", precision = 14, scale = 4)
    private BigDecimal monthlyAmount;

    @Column(name = "monthly_basis", precision = 14, scale = 4)
    private BigDecimal monthlyBasis;

    /** Set on rows created from the default catalog; kept only so seeding stays idempotent. */
    @Column(name = "seed_code", length = 40, updatable = false)
    private String seedCode;

    @Version
    @Builder.Default
    private Long version = 0L;
}
