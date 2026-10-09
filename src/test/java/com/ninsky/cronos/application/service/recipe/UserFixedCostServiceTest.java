package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.request.recipe.UserFixedCostRequest;
import com.ninsky.cronos.domain.model.recipe.UserFixedCost;
import com.ninsky.cronos.domain.port.recipe.UserFixedCostRepositoryPort;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.kitchen.fixedcost.FixedCostSeeder;
import com.ninsky.cronos.kitchen.recipe.FixedCostCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenCaches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR;
import static com.ninsky.cronos.finance.FinanceTestData.ACTOR_ID;
import static com.ninsky.cronos.finance.FinanceTestData.assertViolations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserFixedCostServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private UserFixedCostRepositoryPort repository;
    @Mock
    private RecipeCustomRepository recipes;
    @Mock
    private FixedCostCustomRepository fixedCosts;
    @Mock
    private FixedCostSeeder seeder;
    @Mock
    private KitchenCaches caches;
    @Mock
    private ActorProvider actors;
    @InjectMocks
    private UserFixedCostService service;

    @BeforeEach
    void setUp() {
        when(actors.require()).thenReturn(ACTOR);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static UserFixedCostRequest request(String method, String amount, String percentage, String monthlyAmount, String monthlyBasis) {
        return new UserFixedCostRequest("Mano de obra", null, "LABOR", decimal(amount), decimal(percentage), method, true, decimal(monthlyAmount),
                decimal(monthlyBasis));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private UserFixedCost stored(String method, String amount) {
        UserFixedCost cost = UserFixedCost.builder().id(ID).userId(ACTOR_ID).name("Mano de obra").type("LABOR").calculationMethod(method)
                .defaultAmount(new BigDecimal(amount)).percentage(BigDecimal.ZERO).isActive(true).build();
        when(repository.findByIdAndUserId(ID, ACTOR_ID)).thenReturn(Optional.of(cost));
        return cost;
    }

    @Test
    void storesFourDecimalsAndTheMonthlyPair() {
        var created = service.createFixedCost(request("HOURLY_RATE", "0.4375", null, "70", "160"));

        assertThat(created.defaultAmount()).isEqualByComparingTo("0.4375");
        assertThat(created.monthlyBasis()).isEqualByComparingTo("160");
        assertThat(created.appliesByDefault()).isTrue();
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void monthlyFiguresComeTogether() {
        assertViolations(() -> service.createFixedCost(request("HOURLY_RATE", "75", null, "12000", null)), ApiErrorCode.VALIDATION_ERROR,
                "monthlyBasis");
        assertViolations(() -> service.createFixedCost(request("HOURLY_RATE", "75", null, null, "160")), ApiErrorCode.VALIDATION_ERROR,
                "monthlyAmount");
        assertViolations(() -> service.createFixedCost(request("HOURLY_RATE", "75", null, "12000", "0")), ApiErrorCode.VALIDATION_ERROR,
                "monthlyBasis");
    }

    @Test
    void percentageCostsTakeNoAmountOrMonthlyFigures() {
        assertViolations(() -> service.createFixedCost(request("PERCENTAGE", null, "5", "1", "2")), ApiErrorCode.VALIDATION_ERROR,
                "monthlyAmount");
        assertViolations(() -> service.createFixedCost(request("PERCENTAGE", null, "0", null, null)), ApiErrorCode.VALIDATION_ERROR,
                "percentage");
        var created = service.createFixedCost(request("PERCENTAGE", "99", "5", null, null));
        assertThat(created.defaultAmount()).isEqualByComparingTo("0");
        assertThat(created.percentage()).isEqualByComparingTo("5");
    }

    @Test
    void rejectsMoreThanFourDecimalsAndUnknownMethods() {
        assertViolations(() -> service.createFixedCost(request("PER_UNIT", "0.12345", null, null, null)), ApiErrorCode.VALIDATION_ERROR,
                "defaultAmount");
        assertViolations(() -> service.createFixedCost(request("WEEKLY", "1", null, null, null)), ApiErrorCode.VALIDATION_ERROR,
                "calculationMethod");
    }

    @Test
    void listingSeedsDefaultsFirstAndReturnsInactiveRowsToo() {
        UserFixedCost inactive = UserFixedCost.builder().id(ID).name("x").isActive(false).build();
        when(repository.findByUserId(ACTOR_ID, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(inactive)));

        assertThat(service.getMyFixedCosts(Pageable.unpaged(), null).getContent()).singleElement()
                .satisfies(row -> assertThat(row.isActive()).isFalse());
        verify(seeder).ensureSeeded(ACTOR_ID);
    }

    @Test
    void deactivatingMarksTheRecipesUsingItStale() {
        stored("HOURLY_RATE", "75");
        UUID recipe = UUID.randomUUID();
        when(recipes.idsUsingFixedCost(ID)).thenReturn(List.of(recipe));
        when(recipes.markStale(List.of(recipe))).thenReturn(1);

        assertThat(service.setActive(ID, false).isActive()).isFalse();
        verify(recipes).markStale(List.of(recipe));
    }

    @Test
    void renamingDoesNotTouchRecipesButAnAmountChangeMarksThemStale() {
        stored("HOURLY_RATE", "75");
        service.updateFixedCost(ID, request("HOURLY_RATE", "75.0", null, null, null));
        verify(recipes, never()).markStale(anyCollection());

        service.updateFixedCost(ID, request("HOURLY_RATE", "80", null, null, null));
        verify(recipes).idsUsingFixedCost(ID);
    }

    @Test
    void deletingACostInUseIs409WithTheRecipes() {
        stored("PER_UNIT", "18");
        Map<String, Object> recipe = Map.of("id", UUID.randomUUID(), "name", "Pastel");
        when(recipes.namesUsingFixedCost(ID)).thenReturn(List.of(recipe));

        assertThatThrownBy(() -> service.deleteFixedCost(ID)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.RESOURCE_IN_USE);
            assertThat(e.violations().getFirst().details()).containsEntry("recipes", List.of(recipe));
        });
        verify(repository, never()).delete(any());
    }

    @Test
    void deletingAnUnusedCostDropsDeletedRecipesReferencesFirst() {
        stored("PER_UNIT", "18");
        when(recipes.namesUsingFixedCost(ID)).thenReturn(List.of());

        service.deleteFixedCost(ID);

        var order = org.mockito.Mockito.inOrder(fixedCosts, repository);
        order.verify(fixedCosts).detachFromDeletedRecipes(ID);
        order.verify(repository).delete(ID);
    }

    @Test
    void foreignCostIs404() {
        when(repository.findByIdAndUserId(ID, ACTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(ID, false)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void savedValuesAreTrimmed() {
        ArgumentCaptor<UserFixedCost> saved = ArgumentCaptor.forClass(UserFixedCost.class);
        service.createFixedCost(new UserFixedCostRequest("  Gas  ", "  ", " UTILITY ", BigDecimal.ONE, null, "FIXED_PER_BATCH", null, null, null));

        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Gas");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().getType()).isEqualTo("UTILITY");
        assertThat(saved.getValue().isAppliesByDefault()).isFalse();
    }
}
