package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "roles")
public class RoleJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    /** Stable code (e.g. SUPER_ADMIN); derived by a DB trigger for legacy writers, so read-only here. */
    @Column(insertable = false, updatable = false, length = 50)
    private String code;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<PermissionJpaEntity> permissions = new HashSet<>();
}
