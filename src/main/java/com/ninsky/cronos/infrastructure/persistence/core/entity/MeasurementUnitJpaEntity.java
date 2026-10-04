package com.ninsky.cronos.infrastructure.persistence.core.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity @Getter @Setter
@SQLDelete(sql = "UPDATE measurement_units SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "measurement_units")
public class MeasurementUnitJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
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
    private boolean isBaseUnit;

    @Column(name = "is_system_default", nullable = false)
    @Builder.Default
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
