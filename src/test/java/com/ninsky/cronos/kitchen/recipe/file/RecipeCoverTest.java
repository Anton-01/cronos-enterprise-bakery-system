package com.ninsky.cronos.kitchen.recipe.file;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeRevisions;
import com.ninsky.cronos.kitchen.shared.KitchenProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR;
import static com.ninsky.cronos.finance.FinanceTestData.ACTOR_ID;
import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Baking-studio §2: cover upload, clear and the copy on duplicate. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecipeCoverTest {

    private static final UUID RECIPE = UUID.randomUUID();

    @Mock
    private RecipeCustomRepository recipes;
    @Mock
    private RecipeFileCustomRepository files;
    @Mock
    private RecipeRevisions revisions;
    @Mock
    private StoragePort storage;
    @Mock
    private AuditRecorder audit;
    @Mock
    private ActorProvider actors;

    private RecipeFileService service;

    @BeforeEach
    void setUp() {
        KitchenProperties properties = new KitchenProperties(60, 500, 120, DataSize.ofMegabytes(25), 40, DataSize.ofMegabytes(500), 15, 30, "");
        service = new RecipeFileService(recipes, files, revisions, storage, properties, audit, actors, CLOCK);
        when(actors.require()).thenReturn(ACTOR);
        RecipeAggregate recipe = mock(RecipeAggregate.class);
        RecipeAggregate.Head head = mock(RecipeAggregate.Head.class);
        when(recipe.id()).thenReturn(RECIPE);
        when(recipe.head()).thenReturn(head);
        when(head.id()).thenReturn(RECIPE);
        when(head.name()).thenReturn("Pastel");
        when(head.cost()).thenReturn(new RecipeAggregate.Cost(null, null, null, null, null, null, 0, CostStatus.CURRENT, null));
        when(recipes.findVisible(RECIPE, ACTOR_ID)).thenReturn(Optional.of(recipe));
        when(recipes.lockOwned(RECIPE, ACTOR_ID)).thenReturn(Optional.of(recipe));
        when(recipes.bumpVersion(eq(RECIPE), any(), any())).thenReturn(7L);
        when(files.find(eq(RECIPE), any())).thenAnswer(call -> Optional.of(row(call.getArgument(1), "cover.jpg", true)));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static RecipeFileCustomRepository.Row row(UUID id, String name, boolean cover) {
        return new RecipeFileCustomRepository.Row(id, RECIPE, "recipes/k/" + id + ".jpg", name, FileKind.IMAGE, "image/jpeg", 1000, null, cover,
                "recipes/k/" + id + "_thumb.webp", "recipes/k/" + id + "_card.webp", Instant.EPOCH, null);
    }

    private static MockMultipartFile png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "cover.png", "image/png", out.toByteArray());
    }

    @Test
    void uploadStoresAFileWithCardVariantAndMakesItTheOnlyCover() throws IOException {
        when(files.cover(RECIPE)).thenReturn(Optional.of(row(UUID.randomUUID(), "old.jpg", true)));
        ArgumentCaptor<RecipeFileCustomRepository.NewFile> inserted = ArgumentCaptor.forClass(RecipeFileCustomRepository.NewFile.class);

        RecipeFileResponse response = service.uploadCover(RECIPE, png(1600, 1200));

        verify(files).insert(inserted.capture());
        RecipeFileCustomRepository.NewFile file = inserted.getValue();
        assertThat(file.cardKey()).contains("_card.");
        assertThat(file.thumbnailKey()).contains("_thumb.");
        var order = inOrder(files);
        order.verify(files).insert(any());
        order.verify(files).makeCover(RECIPE, file.id());
        verify(storage, times(3)).put(any(), any(), any());
        assertThat(response.isCover()).isTrue();
    }

    @Test
    void uploadWritesARevisionAndAnAuditEntry() throws IOException {
        when(files.cover(RECIPE)).thenReturn(Optional.empty());

        service.uploadCover(RECIPE, png(800, 600));

        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.captor();
        verify(revisions).write(eq(RECIPE), eq(7L), eq(ACTOR_ID), any(), eq(RecipeRevisions.Reason.of("kitchen.revision.coverChanged", "cover.png")),
                changes.capture(), any());
        assertThat(changes.getValue()).containsKey("cover");
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.RECIPE_COVER_CHANGED);
    }

    @Test
    void narrowImagesAreRejectedOnTheFileField() throws IOException {
        MockMultipartFile narrow = png(599, 400);

        assertThatThrownBy(() -> service.uploadCover(RECIPE, narrow)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.VALIDATION_ERROR);
            assertThat(e.violations().getFirst().field()).isEqualTo("file");
        });
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void nonImagesAre415ByContentNotByName() {
        MockMultipartFile pdf = new MockMultipartFile("file", "cover.png", "image/png", "%PDF-1.7\n%âãÏÓ\n1 0 obj".getBytes());

        assertThatThrownBy(() -> service.uploadCover(RECIPE, pdf)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void countsAgainstTheFileQuota() throws IOException {
        when(files.count(RECIPE)).thenReturn(40);
        MockMultipartFile image = png(800, 600);

        assertThatThrownBy(() -> service.uploadCover(RECIPE, image)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.QUOTA_EXCEEDED));
    }

    @Test
    void clearKeepsTheFileAndIsIdempotent() {
        RecipeFileCustomRepository.Row cover = row(UUID.randomUUID(), "cover.jpg", true);
        when(files.cover(RECIPE)).thenReturn(Optional.of(cover), Optional.empty());

        service.clearCover(RECIPE);
        service.clearCover(RECIPE);

        verify(files, times(1)).unsetCover(cover.id());
        verify(files, never()).delete(any());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(1)).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.RECIPE_COVER_CLEARED);
    }

    @Test
    void invisibleRecipeIs404() {
        when(recipes.findVisible(RECIPE, ACTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clearCover(RECIPE)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void libraryRecipeIsReadOnlyNotMissing() throws IOException {
        RecipeAggregate library = mock(RecipeAggregate.class);
        RecipeAggregate.Head head = mock(RecipeAggregate.Head.class);
        when(library.head()).thenReturn(head);
        when(head.system()).thenReturn(true);
        when(recipes.findVisible(RECIPE, ACTOR_ID)).thenReturn(Optional.of(library));
        MockMultipartFile image = png(800, 600);

        assertThatThrownBy(() -> service.uploadCover(RECIPE, image)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT));
        assertThatThrownBy(() -> service.clearCover(RECIPE)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.primaryCode()).isEqualTo(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT));
        verify(storage, never()).put(any(), any(), any());
        verify(recipes, never()).lockOwned(any(), any());
    }

    @Test
    void duplicateCopiesOnlyTheCoverWithAllItsVariants() {
        UUID copy = UUID.randomUUID();
        when(files.cover(RECIPE)).thenReturn(Optional.of(row(UUID.randomUUID(), "cover.jpg", true)));
        ArgumentCaptor<RecipeFileCustomRepository.NewFile> inserted = ArgumentCaptor.forClass(RecipeFileCustomRepository.NewFile.class);

        service.copyCover(RECIPE, copy, ACTOR_ID, Instant.EPOCH);

        verify(storage, times(3)).copy(any(), any());
        verify(files).insert(inserted.capture());
        assertThat(inserted.getValue().recipeId()).isEqualTo(copy);
        assertThat(inserted.getValue().cover()).isTrue();
        assertThat(List.of(inserted.getValue().storageKey(), inserted.getValue().thumbnailKey(), inserted.getValue().cardKey()))
                .allSatisfy(key -> assertThat(key).startsWith("recipes/" + ACTOR_ID + "/" + copy + "/"));
    }

    @Test
    void duplicateWithoutCoverOrQuotaCopiesNothing() {
        when(files.cover(RECIPE)).thenReturn(Optional.empty());
        service.copyCover(RECIPE, UUID.randomUUID(), ACTOR_ID, Instant.EPOCH);

        when(files.cover(RECIPE)).thenReturn(Optional.of(row(UUID.randomUUID(), "cover.jpg", true)));
        when(files.tenantBytes(ACTOR_ID)).thenReturn(DataSize.ofMegabytes(500).toBytes());
        service.copyCover(RECIPE, UUID.randomUUID(), ACTOR_ID, Instant.EPOCH);

        verify(storage, never()).copy(any(), any());
        verify(files, never()).insert(any());
    }

    @Test
    void cardVariantPreferredForCovers() {
        RecipeFileCustomRepository.Row legacy = new RecipeFileCustomRepository.Row(UUID.randomUUID(), RECIPE, "o.jpg", "o.jpg", FileKind.IMAGE,
                "image/jpeg", 1, null, true, "t.jpg", null, Instant.EPOCH, null);

        assertThat(row(UUID.randomUUID(), "c.jpg", true).coverKey()).contains("_card.");
        assertThat(legacy.coverKey()).isEqualTo("t.jpg");
    }
}
