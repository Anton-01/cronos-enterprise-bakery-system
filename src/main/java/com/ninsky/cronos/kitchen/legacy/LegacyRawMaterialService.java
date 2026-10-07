package com.ninsky.cronos.kitchen.legacy;

import com.ninsky.cronos.application.request.core.CreateRawMaterialRequest;
import com.ninsky.cronos.application.request.core.DensityConversionRequest;
import com.ninsky.cronos.application.request.core.UpdateRawMaterialRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.DensityConversionDto;
import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import com.ninsky.cronos.application.response.core.RawMaterialResponse;
import com.ninsky.cronos.application.service.RawMaterialService;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.kitchen.ingredient.IngredientDetail;
import com.ninsky.cronos.kitchen.ingredient.IngredientFilter;
import com.ninsky.cronos.kitchen.ingredient.IngredientPrice;
import com.ninsky.cronos.kitchen.ingredient.IngredientPriceRequest;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueryCustomRepository;
import com.ninsky.cronos.kitchen.ingredient.IngredientRequest;
import com.ninsky.cronos.kitchen.ingredient.IngredientService;
import com.ninsky.cronos.kitchen.ingredient.IngredientSummary;
import com.ninsky.cronos.kitchen.shared.Dimension;
import com.ninsky.cronos.kitchen.shared.KitchenCodes;
import com.ninsky.cronos.kitchen.shared.KitchenStatus;
import com.ninsky.cronos.kitchen.shared.Scope;
import com.ninsky.cronos.kitchen.shared.StatusRequest;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Deprecated {@code /raw-material/**} (§13) delegating to {@link IngredientService}; ids are the
 * ingredient ids (V19 kept them). Purchase data maps to the own price, density to g/ml (1 cup = 240 ml).
 */
@Service
@RequiredArgsConstructor
public class LegacyRawMaterialService implements RawMaterialService {

    private static final BigDecimal ML_PER_CUP = BigDecimal.valueOf(240);
    private static final int CODE_MAX_LENGTH = 50;

    private final IngredientService ingredients;
    private final IngredientQueryCustomRepository queries;
    private final UnitCatalog units;
    private final ActorProvider actors;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Page<RawMaterialListResponse> getUserRawMaterials(Pageable pageable, String username) {
        int page = pageable.isPaged() ? pageable.getPageNumber() : 0;
        int size = pageable.isPaged() ? pageable.getPageSize() : 100;
        // Legacy list = the tenant's own ingredients
        IngredientFilter own = new IngredientFilter(null, null, null, false, Scope.USER, null, null);
        return LegacyPages.of(ingredients.page(own, page, size, null), this::listRow);
    }

    @Override
    @Transactional(readOnly = true)
    public RawMaterialResponse getRawMaterialById(UUID id) {
        return response(ingredients.detail(id));
    }

    @Override
    @Transactional
    public RawMaterialResponse createRawMaterial(CreateRawMaterialRequest request, String username) {
        UUID tenant = actors.require().id();
        String code = KitchenCodes.unique(KitchenCodes.of(request.name(), "ING", CODE_MAX_LENGTH), CODE_MAX_LENGTH,
                candidate -> queries.codeTaken(tenant, candidate));
        return response(ingredients.create(new IngredientRequest(code, request.name(), request.categoryId(), request.description(),
                request.brand(), dimension(request.purchaseUnitId()), request.yieldPercentage(), density(request.densityConversion()),
                List.of(), List.of(), price(request.purchaseQuantity(), request.purchaseUnitId(), request.unitCost(), request.currency(),
                request.supplier()), null)));
    }

    @Override
    @Transactional
    public RawMaterialResponse updateRawMaterial(UUID id, UpdateRawMaterialRequest request, String userName) {
        IngredientDetail current = ingredients.detail(id);
        IngredientPriceRequest price = price(request.purchaseQuantity(), request.purchaseUnitId(), request.unitCost(), request.currency(),
                request.supplier());
        // Append a price only when the purchase data really changed (history is append-only)
        IngredientPriceRequest changed = samePrice(current.ownPrice(), price) ? null : price;
        return response(ingredients.update(id, new IngredientRequest(null, request.name(), request.categoryId(), request.description(),
                request.brand(), current.summary().baseDimension(), request.yieldPercentage(),
                Optional.ofNullable(density(request.densityConversion())).orElse(current.densityGPerMl()),
                current.summary().allergens().stream().map(a -> a.id()).toList(),
                current.substitutes().stream().filter(s -> s.scope() == Scope.USER)
                        .map(s -> new IngredientRequest.Substitute(s.ingredientId(), s.ratio(), s.notes())).toList(),
                changed, current.version())));
    }

    @Override
    @Transactional
    public void changeStatus(UUID id, ChangeStatusRequest request) {
        IngredientDetail current = ingredients.detail(id);
        KitchenStatus status = request.status() == RecordStatus.ACTIVE ? KitchenStatus.ACTIVE : KitchenStatus.INACTIVE;
        ingredients.changeStatus(id, new StatusRequest(status, current.version()));
    }

    private Dimension dimension(Long purchaseUnitId) {
        return units.find(purchaseUnitId).flatMap(u -> Dimension.of(u.dimension()))
                .orElseThrow(() -> ApiException.invalid("purchaseUnitId", "kitchen.unit.notFound"));
    }

    private IngredientPriceRequest price(BigDecimal quantity, Long unitId, BigDecimal price, String currency, String supplier) {
        return new IngredientPriceRequest(quantity, unitId, price, currency, supplier, LocalDate.now(clock));
    }

    private static boolean samePrice(IngredientPrice own, IngredientPriceRequest next) {
        return own != null && own.purchaseUnitId() == next.purchaseUnitId()
                && own.purchaseQuantity().compareTo(next.purchaseQuantity()) == 0
                && own.price().compareTo(next.price()) == 0
                && own.currency().equalsIgnoreCase(next.currency())
                && Objects.equals(own.supplier(), next.supplier());
    }

    private static BigDecimal density(DensityConversionRequest conversion) {
        return conversion == null || conversion.gramsPerCup() == null ? null
                : conversion.gramsPerCup().divide(ML_PER_CUP, 4, RoundingMode.HALF_EVEN);
    }

    private RawMaterialListResponse listRow(IngredientSummary summary) {
        return new RawMaterialListResponse(summary.id(), summary.name(), summary.categoryName(), summary.baseUnitCode(),
                BigDecimal.ONE, summary.costPerBaseUnit(), summary.yieldPercent(), summary.costPerBaseUnit(), status(summary.status()));
    }

    private RawMaterialResponse response(IngredientDetail detail) {
        IngredientSummary summary = detail.summary();
        Optional<IngredientPrice> price = Optional.ofNullable(detail.ownPrice()).or(() -> Optional.ofNullable(detail.referencePrice()));
        BigDecimal gramsPerCup = detail.densityGPerMl() == null ? null : detail.densityGPerMl().multiply(ML_PER_CUP);
        return RawMaterialResponse.builder().id(summary.id()).name(summary.name()).categoryId(summary.categoryId())
                .description(detail.description()).brand(detail.brand())
                .supplier(price.map(IngredientPrice::supplier).orElse(null))
                .purchaseUnitId(price.map(IngredientPrice::purchaseUnitId).orElse(null))
                .purchaseQuantity(price.map(IngredientPrice::purchaseQuantity).orElse(null))
                .unitCost(price.map(IngredientPrice::price).orElse(null))
                .currency(price.map(IngredientPrice::currency).orElse(null))
                .yieldPercentage(summary.yieldPercent()).baseUnitCost(summary.costPerBaseUnit())
                .status(status(summary.status()).name())
                .densityConversion(gramsPerCup == null ? null : new DensityConversionDto(gramsPerCup,
                        gramsPerCup.divide(BigDecimal.valueOf(16), 4, RoundingMode.HALF_EVEN),
                        gramsPerCup.divide(BigDecimal.valueOf(48), 4, RoundingMode.HALF_EVEN)))
                .build();
    }

    private static RecordStatus status(KitchenStatus status) {
        return status == KitchenStatus.ACTIVE ? RecordStatus.ACTIVE : RecordStatus.INACTIVE;
    }
}
