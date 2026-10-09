package com.ninsky.cronos.infrastructure.persistence.recipe.mapper;

import com.ninsky.cronos.domain.model.recipe.UserFixedCost;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.UserFixedCostJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserFixedCostMapper {

    public UserFixedCost toDomain(UserFixedCostJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserFixedCost.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .defaultAmount(entity.getDefaultAmount())
                .percentage(entity.getPercentage())
                .calculationMethod(entity.getCalculationMethod())
                .isActive(entity.isActive())
                .appliesByDefault(entity.isAppliesByDefault())
                .monthlyAmount(entity.getMonthlyAmount())
                .monthlyBasis(entity.getMonthlyBasis())
                .seedCode(entity.getSeedCode())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public UserFixedCostJpaEntity toEntity(UserFixedCost domain) {
        if (domain == null) {
            return null;
        }
        UserFixedCostJpaEntity entity = UserFixedCostJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .name(domain.getName())
                .description(domain.getDescription())
                .type(domain.getType())
                .defaultAmount(domain.getDefaultAmount())
                .percentage(domain.getPercentage())
                .calculationMethod(domain.getCalculationMethod())
                .isActive(domain.isActive())
                .appliesByDefault(domain.isAppliesByDefault())
                .monthlyAmount(domain.getMonthlyAmount())
                .monthlyBasis(domain.getMonthlyBasis())
                .seedCode(domain.getSeedCode())
                .version(domain.getVersion())
                .build();
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}
