package com.ninsky.cronos.infrastructure.persistence.core.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity @Getter @Setter
@Table(name = "measurement_units")
public class MeasurementUnitJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codeIdentity;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_plural", length = 100)
    private String namePlural;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_type_id", nullable = false)
    private UnitTypeJpaEntity unitType;

    @Column(name = "multiplier_to_base", nullable = false, precision = 20, scale = 10)
    private BigDecimal multiplierToBase;

    @Column(name = "is_base_unit", nullable = false)
    private boolean isBaseUnit = false;

    @Column(name = "is_system_default", nullable = false)
    private boolean isSystemDefault = true;

    @Column(name = "user_id")
    private Long userId; // Null para los del sistema

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
