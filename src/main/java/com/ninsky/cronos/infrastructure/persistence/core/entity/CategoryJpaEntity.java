package com.ninsky.cronos.infrastructure.persistence.core.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryStatus;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder @Entity @Getter @Setter
// Two placeholders, not one: unlike UnitTypeJpaEntity (no @Version), this entity is
// optimistic-lock-versioned, and Hibernate always appends "AND version = ?" to a custom
// @SQLDelete's WHERE clause for a versioned entity — verified against a real Postgres instance;
// omitting the second placeholder fails at runtime ("column index out of range: 2") only when the
// delete actually executes, not at startup, so it doesn't show up until you exercise it.
@SQLDelete(sql = "UPDATE categories SET status = 'TRASHED', deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
@SQLRestriction("status <> 'TRASHED'")
@Table(name = "categories")
public class CategoryJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryScope scope;

    /** Non-null iff {@code scope == USER}; enforced at the DB level by {@code chk_categories_scope_user_id}. */
    @Column(name = "user_id")
    private UUID userId;

    @Version
    @Builder.Default
    private Long version = 1L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CategoryStatus status = CategoryStatus.ACTIVE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
