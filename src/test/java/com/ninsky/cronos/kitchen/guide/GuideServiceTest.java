package com.ninsky.cronos.kitchen.guide;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ninsky.cronos.finance.FinanceTestData.ACTOR;
import static com.ninsky.cronos.finance.FinanceTestData.ACTOR_ID;
import static com.ninsky.cronos.finance.FinanceTestData.CLOCK;
import static com.ninsky.cronos.finance.FinanceTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuideServiceTest {

    @Mock
    private GuideCustomRepository store;
    @Mock
    private AuditRecorder audit;
    @Mock
    private ActorProvider actors;

    private GuideService service;

    private static final PanSizeRequest ROUND = new PanSizeRequest(PanShape.ROUND, "Mi molde", new BigDecimal("18"), null, null,
            new BigDecimal("7"), null, null, null);

    @BeforeEach
    void setUp() {
        service = new GuideService(store, audit, actors, CLOCK);
        when(actors.require()).thenReturn(ACTOR);
    }

    private static GuideCustomRepository.PanRow pan(UUID id, UUID owner) {
        return new GuideCustomRepository.PanRow(id, "P", owner, ROUND, Map.of(), 0, null);
    }

    private static void assertCode(Runnable call, ApiErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.primaryCode()).isEqualTo(code));
    }

    @Test
    void systemPanIs403AndAnotherUsersIs404() {
        UUID system = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(store.pan(system)).thenReturn(Optional.of(pan(system, null)));
        when(store.pan(foreign)).thenReturn(Optional.of(pan(foreign, UUID.randomUUID())));

        assertCode(() -> service.updatePan(system, ROUND), ApiErrorCode.ACCESS_DENIED);
        assertCode(() -> service.deletePan(foreign), ApiErrorCode.RESOURCE_NOT_FOUND);
        verify(store, never()).deletePan(any(), any());
    }

    @Test
    void ownPansHaveAQuotaAndUniqueNames() {
        when(store.userPanCount(ACTOR_ID)).thenReturn(50);
        assertCode(() -> service.createPan(ROUND), ApiErrorCode.QUOTA_EXCEEDED);

        when(store.userPanNameTaken(ACTOR_ID, "Mi molde", null)).thenReturn(true);
        assertCode(() -> service.createPan(ROUND), ApiErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void createdPanGetsAUserCodeAndItsUnusedDimensionsDropped() {
        ArgumentCaptor<GuideCustomRepository.PanRow> row = ArgumentCaptor.forClass(GuideCustomRepository.PanRow.class);
        when(store.pan(any())).thenAnswer(call -> Optional.of(pan(call.getArgument(0), ACTOR_ID)));

        service.createPan(new PanSizeRequest(PanShape.ROUND, " Mi molde ", new BigDecimal("18"), BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("7"), null, null, null));

        verify(store).upsertPan(row.capture());
        assertThat(row.getValue().code()).isEqualTo(GuideService.userPanCode(row.getValue().id())).matches("^USER_[0-9A-F]{32}$");
        assertThat(row.getValue().ownerId()).isEqualTo(ACTOR_ID);
        assertThat(row.getValue().size().lengthCm()).isNull();
        assertThat(row.getValue().size().name()).isEqualTo("Mi molde");
    }

    @Test
    void etagVariesByLanguageAndContent() {
        when(store.fingerprint(ACTOR_ID)).thenReturn("2026-10-08|22@x", "2026-10-08|22@x", "2026-10-08|23@y");

        String es = service.etag(GuideLanguage.of("es-MX"));
        String en = service.etag(GuideLanguage.of("en"));
        String changed = service.etag(GuideLanguage.of("es-MX"));

        assertThat(es).startsWith("\"").endsWith("\"").isNotEqualTo(en).isNotEqualTo(changed);
    }

    @Test
    void readersGetLocalisedContentWithBaseFallback() {
        GuideCustomRepository.ArticleRow article = new GuideCustomRepository.ArticleRow(UUID.randomUUID(), "FS_A", GuideCategory.FOOD_SAFETY,
                "Título", "Resumen", "pi pi-shield", List.of("t"), List.of(new GuideBlock.Paragraph("Texto")), List.of(),
                Map.of("en", new GuideAdmin.ArticleTranslation("Title", " ", null, List.of())), 10, true, null);
        when(store.articles(true)).thenReturn(List.of(article));
        when(store.revision()).thenReturn(Optional.of(LocalDate.of(2026, 10, 8)));

        BakingGuide guide = service.guide(GuideLanguage.of("en-US"));

        GuideArticle read = guide.articles().getFirst();
        assertThat(read.title()).isEqualTo("Title");
        assertThat(read.summary()).isEqualTo("Resumen");
        assertThat(read.blocks()).containsExactly(new GuideBlock.Paragraph("Texto"));
        assertThat(guide.revision()).isEqualTo("2026-10-08");
    }

    @Test
    void staffEditsBumpTheRevisionAndAreAudited() {
        GuideAdmin.ArticleRequest request = new GuideAdmin.ArticleRequest("FS_NEW", GuideCategory.FOOD_SAFETY, "Título", "Resumen",
                "pi pi-shield", List.of(), List.of(new GuideBlock.Paragraph("Texto")), List.of(), 5, null, Map.of());
        when(store.article(any())).thenAnswer(call -> Optional.of(new GuideCustomRepository.ArticleRow(call.getArgument(0), "FS_NEW",
                GuideCategory.FOOD_SAFETY, "Título", "Resumen", "pi pi-shield", List.of(), List.of(), List.of(), Map.of(), 5, true, null)));

        service.createArticle(request);

        verify(store).setRevision(TODAY);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.GUIDE_ARTICLE_CREATED);
    }

    @Test
    void duplicateArticleCodeIs409() {
        when(store.articleCodeTaken(anyString(), any())).thenReturn(true);
        GuideAdmin.ArticleRequest request = new GuideAdmin.ArticleRequest("FS_NEW", GuideCategory.FOOD_SAFETY, "Título", "Resumen",
                "pi pi-shield", List.of(), List.of(new GuideBlock.Paragraph("Texto")), List.of(), 5, null, Map.of());

        assertCode(() -> service.createArticle(request), ApiErrorCode.DUPLICATE_RESOURCE);
        verify(store, never()).upsertArticle(any());
    }
}
