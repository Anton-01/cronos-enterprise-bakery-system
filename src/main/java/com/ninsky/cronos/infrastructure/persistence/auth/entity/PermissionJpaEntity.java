package com.ninsky.cronos.infrastructure.persistence.auth.entity;

import com.ninsky.cronos.domain.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "permissions") @Entity
public class PermissionJpaEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String resource;

    @Column(length = 50)
    private String action;

    @ManyToMany(mappedBy = "permissions")
    @Builder.Default
    private Set<RoleJpaEntity> roles = new HashSet<>();
}
