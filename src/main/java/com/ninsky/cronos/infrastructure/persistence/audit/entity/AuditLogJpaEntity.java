package com.ninsky.cronos.infrastructure.persistence.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private com.ninsky.cronos.domain.model.audit.AuditAction action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private String changes;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "outcome", nullable = false, length = 10)
    private String outcome;

    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Legacy writers only know the action; classification follows from it. */
    @PrePersist
    void classify() {
        category = category != null ? category : action.category().name();
        outcome = outcome != null ? outcome : com.ninsky.cronos.domain.model.audit.AuditOutcome.SUCCESS.name();
        severity = severity != null ? severity : com.ninsky.cronos.domain.model.audit.AuditSeverity.NOTICE.name();
    }
}
