package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.request.recipe.UserFixedCostRequest;
import com.ninsky.cronos.application.response.recipe.UserFixedCostResponse;
import com.ninsky.cronos.domain.model.recipe.UserFixedCost;
import com.ninsky.cronos.domain.port.recipe.UserFixedCostRepositoryPort;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.costing.FixedCostMethod;
import com.ninsky.cronos.kitchen.fixedcost.FixedCostSeeder;
import com.ninsky.cronos.kitchen.recipe.FixedCostCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import com.ninsky.cronos.kitchen.shared.Numbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The caller's fixed-cost catalog (baking-studio §4). {@code defaultAmount} is the value the cost engine uses;
 * the monthly figures only record how it was derived. Changes that alter a recipe's cost mark those recipes STALE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFixedCostService {

    private static final String MAX_AMOUNT = "9999999999.9999";

    private final UserFixedCostRepositoryPort fixedCostRepository;
    private final RecipeCustomRepository recipes;
    private final FixedCostCustomRepository fixedCosts;
    private final FixedCostSeeder seeder;
    private final KitchenCaches caches;
    private final ActorProvider actors;

    @Transactional
    public UserFixedCostResponse createFixedCost(UserFixedCostRequest request) {
        UUID userId = actors.require().id();
        FixedCostMethod method = validate(request);
        UserFixedCost fixedCost = UserFixedCost.builder().userId(userId).isActive(true).build();
        apply(fixedCost, request, method);
        return mapToResponse(fixedCostRepository.save(fixedCost));
    }

    /** Active and inactive rows; the first call of a user seeds the default catalog (§4.6). */
    @Transactional
    public Page<UserFixedCostResponse> getMyFixedCosts(Pageable pageable, String search) {
        UUID userId = actors.require().id();
        seeder.ensureSeeded(userId);
        Page<UserFixedCost> costs = search != null && !search.isBlank()
                ? fixedCostRepository.findByUserIdAndNameContainingIgnoreCase(userId, search.strip(), pageable)
                : fixedCostRepository.findByUserId(userId, pageable);
        return costs.map(this::mapToResponse);
    }

    @Transactional
    public UserFixedCostResponse updateFixedCost(UUID id, UserFixedCostRequest request) {
        UUID userId = actors.require().id();
        UserFixedCost fixedCost = owned(id, userId);
        FixedCostMethod method = validate(request);
        boolean costChanged = !Objects.equals(method.name(), fixedCost.getCalculationMethod())
                || !sameAmount(fixedCost.getDefaultAmount(), amountOf(request, method))
                || !sameAmount(fixedCost.getPercentage(), percentageOf(request, method));
        apply(fixedCost, request, method);
        UserFixedCost saved = fixedCostRepository.save(fixedCost);
        if (costChanged) {
            markRecipesStale(id, userId);
        }
        return mapToResponse(saved);
    }

    /** §4.2: deactivated costs stay on the recipes that use them (and keep being costed); new recipes cannot add them. */
    @Transactional
    public UserFixedCostResponse setActive(UUID id, boolean active) {
        UUID userId = actors.require().id();
        UserFixedCost fixedCost = owned(id, userId);
        if (fixedCost.isActive() == active) {
            return mapToResponse(fixedCost);
        }
        fixedCost.setActive(active);
        UserFixedCost saved = fixedCostRepository.save(fixedCost);
        markRecipesStale(id, userId);
        return mapToResponse(saved);
    }

    /** A cost used by a live recipe cannot be deleted (409 with the recipes); deactivating it is the alternative. */
    @Transactional
    public void deleteFixedCost(UUID id) {
        UUID userId = actors.require().id();
        owned(id, userId);
        List<Map<String, Object>> using = recipes.namesUsingFixedCost(id);
        if (!using.isEmpty()) {
            throw ApiException.withDetails(ApiErrorCode.RESOURCE_IN_USE, null, Map.of("recipes", using), "kitchen.fixedCost.inUse", using.size());
        }
        fixedCosts.detachFromDeletedRecipes(id);
        fixedCostRepository.delete(id);
        log.info("Fixed cost {} deleted by {}", id, userId);
    }

    @Transactional
    public int restoreDefaults() {
        return seeder.restoreDefaults(actors.require().id());
    }

    /** Field rules of §4.1; returns the parsed calculation method. */
    private static FixedCostMethod validate(UserFixedCostRequest request) {
        Violations violations = new Violations();
        Optional<FixedCostMethod> method = FixedCostMethod.parse(request.calculationMethod());
        violations.invalidIf(method.isEmpty(), "calculationMethod", "kitchen.fixedCost.method");
        boolean percentage = method.map(m -> m == FixedCostMethod.PERCENTAGE).orElse(false);
        if (percentage) {
            violations.invalidIf(request.percentage() == null || request.percentage().signum() <= 0
                    || !Numbers.within(request.percentage(), "0", "100", 2), "percentage", "kitchen.fixedCost.percentageRequired");
            violations.invalidIf(request.monthlyAmount() != null || request.monthlyBasis() != null, "monthlyAmount",
                    "kitchen.fixedCost.monthlyNotApplicable");
        } else if (method.isPresent()) {
            violations.invalidIf(!Numbers.within(request.defaultAmount(), "0", MAX_AMOUNT, 4), "defaultAmount", "kitchen.fixedCost.amountRequired");
            if ((request.monthlyAmount() == null) != (request.monthlyBasis() == null)) {
                violations.invalid(request.monthlyAmount() == null ? "monthlyAmount" : "monthlyBasis", "kitchen.fixedCost.monthlyPair");
            } else if (request.monthlyAmount() != null) {
                violations.invalidIf(!Numbers.within(request.monthlyAmount(), "0", MAX_AMOUNT, 4), "monthlyAmount", "api.validation.range", 0,
                        MAX_AMOUNT);
                violations.invalidIf(!Numbers.positive(request.monthlyBasis(), MAX_AMOUNT, 4), "monthlyBasis", "kitchen.fixedCost.monthlyBasis");
            }
        }
        violations.throwIfAny();
        return method.orElseThrow();
    }

    private static void apply(UserFixedCost fixedCost, UserFixedCostRequest request, FixedCostMethod method) {
        boolean percentage = method == FixedCostMethod.PERCENTAGE;
        fixedCost.setName(request.name().strip());
        fixedCost.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().strip());
        fixedCost.setType(request.type().strip());
        fixedCost.setCalculationMethod(method.name());
        fixedCost.setDefaultAmount(amountOf(request, method));
        fixedCost.setPercentage(percentageOf(request, method));
        fixedCost.setAppliesByDefault(Boolean.TRUE.equals(request.appliesByDefault()));
        fixedCost.setMonthlyAmount(percentage ? null : request.monthlyAmount());
        fixedCost.setMonthlyBasis(percentage ? null : request.monthlyBasis());
    }

    private static BigDecimal amountOf(UserFixedCostRequest request, FixedCostMethod method) {
        return method == FixedCostMethod.PERCENTAGE || request.defaultAmount() == null ? BigDecimal.ZERO : request.defaultAmount();
    }

    private static BigDecimal percentageOf(UserFixedCostRequest request, FixedCostMethod method) {
        return method == FixedCostMethod.PERCENTAGE ? request.percentage() : BigDecimal.ZERO;
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private void markRecipesStale(UUID fixedCostId, UUID userId) {
        if (recipes.markStale(recipes.idsUsingFixedCost(fixedCostId)) > 0) {
            caches.evict(KitchenCaches.STATS, KitchenCaches.statsKey("recipes", userId));
        }
    }

    private UserFixedCost owned(UUID id, UUID userId) {
        return fixedCostRepository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("kitchen.fixedCost.notFound"));
    }

    private UserFixedCostResponse mapToResponse(UserFixedCost cost) {
        return UserFixedCostResponse.builder().id(cost.getId()).name(cost.getName()).description(cost.getDescription())
                .type(cost.getType()).defaultAmount(cost.getDefaultAmount()).percentage(cost.getPercentage())
                .calculationMethod(cost.getCalculationMethod()).isActive(cost.isActive()).appliesByDefault(cost.isAppliesByDefault())
                .monthlyAmount(cost.getMonthlyAmount()).monthlyBasis(cost.getMonthlyBasis())
                .createdAt(cost.getCreatedAt()).updatedAt(cost.getUpdatedAt()).build();
    }
}
