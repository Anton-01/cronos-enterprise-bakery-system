package com.ninsky.cronos.kitchen.section;

import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.kitchen.shared.KitchenSettingsCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR;
import static com.ninsky.cronos.finance.FinanceTestData.ACTOR_ID;
import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static com.ninsky.cronos.finance.FinanceTestData.assertViolations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecipeSectionServiceTest {

    @Mock
    private RecipeSectionCustomRepository sections;
    @Mock
    private KitchenSettingsCustomRepository settings;
    @Mock
    private ActorProvider actors;

    private RecipeSectionService service;

    @BeforeEach
    void setUp() {
        service = new RecipeSectionService(sections, settings, actors, CLOCK);
        when(actors.require()).thenReturn(ACTOR);
        when(settings.peek(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(true, true));
    }

    @Test
    void normalizesWhitespace() {
        assertThat(RecipeSectionService.normalize("  Baño   /\talmíbar ")).isEqualTo("Baño / almíbar");
        assertThat(RecipeSectionService.normalize("   ")).isNull();
    }

    @Test
    void firstListSeedsDefaultsAndUsedSectionsOnceUnderTheLock() {
        when(settings.peek(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(false, false));
        when(settings.lock(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(false, false));
        when(sections.count(ACTOR_ID)).thenReturn(10);

        service.list();

        verify(sections).insertMissingDefaults(eq(ACTOR_ID), eq(RecipeSectionDefaults.ALL), eq(RecipeSectionService.MAX_SECTIONS), any());
        verify(sections).insertUsedSections(eq(ACTOR_ID), eq(50), any());
        verify(settings).markSectionsSeeded(eq(ACTOR_ID), any());
    }

    @Test
    void anotherTabThatSeededFirstWins() {
        when(settings.peek(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(false, false));
        when(settings.lock(ACTOR_ID)).thenReturn(new KitchenSettingsCustomRepository.Seeded(true, false));

        service.list();

        verify(sections, never()).insertMissingDefaults(any(), any(), anyInt(), any());
    }

    @Test
    void duplicateUnderTheSectionKeyIs409OnName() {
        when(sections.keyTaken(ACTOR_ID, "bano / ALMIBAR", null)).thenReturn(true);

        assertViolations(() -> service.create(new RecipeSectionRequest(" bano  /  ALMIBAR ", null)), ApiErrorCode.DUPLICATE_RESOURCE, "name");
    }

    @Test
    void validatesLengthAndColour() {
        assertViolations(() -> service.create(new RecipeSectionRequest("x".repeat(41), "#12345")), ApiErrorCode.VALIDATION_ERROR, "name",
                ApiErrorCode.VALIDATION_ERROR, "color");
    }

    @Test
    void quotaOfSixty() {
        when(sections.count(ACTOR_ID)).thenReturn(60);

        assertThatThrownBy(() -> service.create(new RecipeSectionRequest("Nueva", null))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.QUOTA_EXCEEDED));
    }

    @Test
    void colourIsStoredLowerCase() {
        UUID id = UUID.randomUUID();
        when(sections.find(any(), any())).thenReturn(Optional.of(new RecipeSection(id, "Merengue", "#ffaa00", 10, 0)));

        service.create(new RecipeSectionRequest("Merengue", "#FFAA00"));

        verify(sections).insert(eq(ACTOR_ID), any(), eq("Merengue"), eq("#ffaa00"), any());
    }

    @Test
    void reorderPutsListedIdsFirstAndKeepsTheRestInOrder() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        when(sections.orderedIds(ACTOR_ID)).thenReturn(List.of(a, b, c));

        service.reorder(new RecipeSectionOrderRequest(List.of(c)));

        verify(sections).reorder(eq(ACTOR_ID), eq(List.of(c, a, b)), any());
    }

    @Test
    void reorderRejectsUnknownAndRepeatedIds() {
        UUID a = UUID.randomUUID();
        when(sections.orderedIds(ACTOR_ID)).thenReturn(List.of(a));

        assertViolations(() -> service.reorder(new RecipeSectionOrderRequest(List.of(a, a, UUID.randomUUID()))),
                ApiErrorCode.VALIDATION_ERROR, "ids[1]", ApiErrorCode.VALIDATION_ERROR, "ids[2]");
        verify(sections, never()).reorder(any(), any(), any());
    }

    @Test
    void deletingAnUnknownLabelIs404() {
        when(sections.delete(eq(ACTOR_ID), any())).thenReturn(false);

        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void restoreDefaultsRespectsTheQuota() {
        when(sections.count(ACTOR_ID)).thenReturn(57);

        service.restoreDefaults();

        verify(sections).insertMissingDefaults(eq(ACTOR_ID), eq(RecipeSectionDefaults.ALL), eq(3), any());
    }
}
