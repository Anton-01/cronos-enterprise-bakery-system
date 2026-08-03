package com.ninsky.cronos.infrastructure.persistence.core.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "ingredient_conversions")
public class IngredientConversionJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "volume_unit_id", nullable = false)
    private MeasurementUnitJpaEntity volumeUnit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mass_unit_id", nullable = false)
    private MeasurementUnitJpaEntity massUnit;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal factor;

    @Column(name = "user_id")
    private Long userId;
}
