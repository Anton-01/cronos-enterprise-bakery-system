package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.request.recipe.UserFixedCostRequest;
import com.ninsky.cronos.application.response.recipe.UserFixedCostResponse;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.entity.recipes.UserFixedCost;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.recipe.UserFixedCostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFixedCostService {

    private final UserFixedCostRepository fixedCostRepository;
    private final UserRepositoryPort userRepository;

    @Transactional
    public UserFixedCostResponse createFixedCost(String username, UserFixedCostRequest request) {
        log.info("Creating a new fixed cost in the catalog for the user: {}", username);
        User user = userRepository.findByUsername(username).orElseThrow();

        if ("PERCENTAGE".equals(request.calculationMethod())) {
            if (request.percentage() == null || request.percentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("El porcentaje es obligatorio y debe ser mayor a 0 para este método de cálculo.");
            }
        } else {
            if (request.defaultAmount() == null || request.defaultAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("El monto por defecto es obligatorio para los métodos de tarifa fija, hora o unidad.");
            }
        }

        BigDecimal safeAmount = request.defaultAmount() != null ? request.defaultAmount() : BigDecimal.ZERO;
        BigDecimal safePercentage = request.percentage() != null ? request.percentage() : BigDecimal.ZERO;

        // 3. Construcción de la Entidad
        UserFixedCost fixedCost = UserFixedCost.builder().userId(user.getId()).name(request.name())
                .description(request.description()).type(request.type()).defaultAmount(safeAmount)
                .percentage(safePercentage).calculationMethod(request.calculationMethod()).isActive(true).build();

        fixedCost = fixedCostRepository.save(fixedCost);
        return mapToResponse(fixedCost);
    }

    @Transactional(readOnly = true)
    public Page<UserFixedCostResponse> getMyFixedCosts(String username, Pageable pageable, String search) {
        log.info("Generating a fixed-cost catalog for: {}", username);
        User user = userRepository.findByUsername(username).orElseThrow();

        Page<UserFixedCost> costs;
        if (search != null && !search.trim().isEmpty()) {
            costs = fixedCostRepository.findByUserIdAndIsActiveTrueAndNameContainingIgnoreCase(user.getId(), search, pageable);
        } else {
            costs = fixedCostRepository.findByUserIdAndIsActiveTrue(user.getId(), pageable);
        }

        return costs.map(this::mapToResponse);
    }

    @Transactional
    public UserFixedCostResponse updateFixedCost(String username, UUID id, UserFixedCostRequest request) {
        log.info("Updating fixed cost {} for the user: {}", id, username);
        User user = userRepository.findByUsername(username).orElseThrow();

        UserFixedCost fixedCost = fixedCostRepository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Costo fijo no encontrado o sin permisos"));

        fixedCost.setName(request.name());
        fixedCost.setDescription(request.description());
        fixedCost.setType(request.type());
        fixedCost.setDefaultAmount(request.defaultAmount());
        fixedCost.setCalculationMethod(request.calculationMethod());

        fixedCost = fixedCostRepository.save(fixedCost);
        return mapToResponse(fixedCost);
    }

    @Transactional
    public void deleteFixedCost(String username, UUID id) {
        log.warn("Performing a soft delete of the fixed cost {} for the user: {}", id, username);
        User user = userRepository.findByUsername(username).orElseThrow();

        UserFixedCost fixedCost = fixedCostRepository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Costo fijo no encontrado o sin permisos"));

        fixedCost.setActive(false);
        fixedCostRepository.save(fixedCost);
    }

    private UserFixedCostResponse mapToResponse(UserFixedCost cost) {
        return UserFixedCostResponse.builder().id(cost.getId()).name(cost.getName()).description(cost.getDescription())
                .type(cost.getType()).defaultAmount(cost.getDefaultAmount()).percentage(cost.getPercentage())
                .calculationMethod(cost.getCalculationMethod()).isActive(cost.isActive()).createdAt(cost.getCreatedAt())
                .updatedAt(cost.getUpdatedAt()).build();
    }
}
