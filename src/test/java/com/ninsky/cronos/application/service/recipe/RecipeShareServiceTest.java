package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.request.recipe.CreateRecipeShareRequest;
import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.domain.port.recipe.RecipeShareAccessLogRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareRepositoryPort;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR;
import static com.ninsky.cronos.finance.FinanceTestData.ACTOR_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The 404 on {@code GET /recipes/{id}/shares} for SYSTEM library recipes, and link ownership. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecipeShareServiceTest {

    private static final UUID LIBRARY = UUID.fromString("2d67939d-6125-5709-140f-a95f02f9eaaf");

    @Mock
    private RecipeShareRepositoryPort shares;
    @Mock
    private RecipeShareAccessLogRepositoryPort accessLogs;
    @Mock
    private RecipeCustomRepository recipes;
    @Mock
    private ActorProvider actors;
    @InjectMocks
    private RecipeShareService service;

    @BeforeEach
    void setUp() {
        when(actors.require()).thenReturn(ACTOR);
        ReflectionTestUtils.setField(service, "frontendUrlSharePublicRecipe", "https://app/shared/recipe/");
    }

    private static RecipeAggregate recipe(UUID id, UUID owner) {
        RecipeAggregate recipe = mock(RecipeAggregate.class);
        RecipeAggregate.Head head = mock(RecipeAggregate.Head.class);
        when(recipe.id()).thenReturn(id);
        when(recipe.head()).thenReturn(head);
        when(head.ownerId()).thenReturn(owner);
        return recipe;
    }

    private static RecipeShare share(UUID recipeId, UUID userId) {
        return RecipeShare.builder().id(UUID.randomUUID()).recipeId(recipeId).userId(userId).shareToken("t").viewsCount(0).build();
    }

    @Test
    void listsOnlyTheCallersLinksOfALibraryRecipe() {
        RecipeAggregate library = recipe(LIBRARY, null);
        when(recipes.findVisible(LIBRARY, ACTOR_ID)).thenReturn(Optional.of(library));
        when(shares.findByRecipeIdAndUserIdOrderByCreatedAtDesc(LIBRARY, ACTOR_ID)).thenReturn(List.of(share(LIBRARY, ACTOR_ID)));

        assertThat(service.getSharesByRecipeId(LIBRARY)).singleElement()
                .satisfies(link -> assertThat(link.shareUrl()).isEqualTo("https://app/shared/recipe/t"));
    }

    @Test
    void invisibleRecipeIs404() {
        when(recipes.findVisible(LIBRARY, ACTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSharesByRecipeId(LIBRARY)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void libraryRecipesCanBeShared() {
        RecipeAggregate library = recipe(LIBRARY, null);
        when(recipes.findVisible(LIBRARY, ACTOR_ID)).thenReturn(Optional.of(library));
        when(shares.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.generateShareLink(LIBRARY, new CreateRecipeShareRequest(3, null)).shareUrl()).startsWith("https://app/shared/recipe/");
    }

    @Test
    void anotherUsersLinkIs404AndNeverRevoked() {
        RecipeShare foreign = share(LIBRARY, UUID.randomUUID());
        when(shares.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.revokeShareLink(LIBRARY, foreign.getId())).isInstanceOf(ApiException.class);
        verify(shares, never()).save(any());
    }

    @Test
    void linkOfAnotherRecipeIs404() {
        RecipeShare mine = share(UUID.randomUUID(), ACTOR_ID);
        when(shares.findById(mine.getId())).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> service.getShareAnalytics(LIBRARY, mine.getId())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void unknownLinkIs404NotA500() {
        UUID missing = UUID.randomUUID();
        when(shares.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getShareAnalytics(LIBRARY, missing)).isInstanceOf(ApiException.class);
    }
}
