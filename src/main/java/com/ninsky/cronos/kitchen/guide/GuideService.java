package com.ninsky.cronos.kitchen.guide;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The baker's guide (baking-studio §6): SYSTEM articles, pans and conversions maintained by platform staff, plus
 * each user's own pans. Reads are localised per request; staff edits bump the content revision and are audited.
 */
@Service
@RequiredArgsConstructor
public class GuideService {

    static final int MAX_USER_PANS = 50;
    private static final String USER_PAN_CODE_PREFIX = "USER_";

    private final GuideCustomRepository store;
    private final AuditRecorder audit;
    private final ActorProvider actors;
    private final Clock clock;

    // ─── readers ────────────────────────────────────────────────────────────────────────

    /** Strong validator of what {@link #guide} would return for this caller and language (cheap: one query). */
    @Transactional(readOnly = true)
    public String etag(GuideLanguage language) {
        String fingerprint = store.fingerprint(actors.require().id()) + "|" + language.key();
        return "\"" + sha256(fingerprint).substring(0, 32) + "\"";
    }

    @Transactional(readOnly = true)
    public BakingGuide guide(GuideLanguage language) {
        UUID owner = actors.require().id();
        List<GuideArticle> articles = store.articles(true).stream().map(row -> localized(row, language)).toList();
        List<PanSize> pans = store.pans(owner).stream().map(row -> localized(row, language)).toList();
        List<IngredientConversion> conversions = store.conversions().stream().map(row -> localized(row, language)).toList();
        String revision = store.revision().map(Object::toString).orElse(null);
        return new BakingGuide(articles, pans, conversions, revision);
    }

    // ─── the caller's own pans ──────────────────────────────────────────────────────────

    @Transactional
    public PanSize createPan(PanSizeRequest request) {
        UUID owner = actors.require().id();
        PanSizeRequest pan = validUserPan(owner, request, null);
        if (store.userPanCount(owner) >= MAX_USER_PANS) {
            throw ApiException.of(ApiErrorCode.QUOTA_EXCEEDED, null, "kitchen.guide.panQuota", MAX_USER_PANS);
        }
        UUID id = UUID.randomUUID();
        try {
            store.upsertPan(new GuideCustomRepository.PanRow(id, userPanCode(id), owner, pan, java.util.Map.of(), 0, null));
        } catch (DuplicateKeyException race) {
            throw duplicatePan();
        }
        return response(store.pan(id).orElseThrow());
    }

    /** Own USER pans only: a SYSTEM pan is 403, another user's is 404 (it does not exist for the caller). */
    @Transactional
    public PanSize updatePan(UUID id, PanSizeRequest request) {
        UUID owner = actors.require().id();
        GuideCustomRepository.PanRow current = ownPan(id, owner);
        PanSizeRequest pan = validUserPan(owner, request, id);
        try {
            store.updatePan(new GuideCustomRepository.PanRow(id, current.code(), owner, pan, current.translations(), 0, null));
        } catch (DuplicateKeyException race) {
            throw duplicatePan();
        }
        return response(store.pan(id).orElseThrow());
    }

    @Transactional
    public void deletePan(UUID id) {
        UUID owner = actors.require().id();
        ownPan(id, owner);
        store.deletePan(id, owner);
    }

    // ─── staff: SYSTEM content ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GuideAdmin.Article> adminArticles() {
        return store.articles(false).stream().map(GuideService::admin).toList();
    }

    @Transactional(readOnly = true)
    public GuideAdmin.Article adminArticle(UUID id) {
        return store.article(id).map(GuideService::admin).orElseThrow(GuideService::articleNotFound);
    }

    @Transactional
    public GuideAdmin.Article createArticle(GuideAdmin.ArticleRequest request) {
        validArticle(request, null);
        UUID id = UUID.randomUUID();
        store.upsertArticle(articleRow(id, request));
        contentChanged(AuditAction.GUIDE_ARTICLE_CREATED, AuditTargets.GUIDE_ARTICLE, id.toString(), request.code());
        return adminArticle(id);
    }

    @Transactional
    public GuideAdmin.Article updateArticle(UUID id, GuideAdmin.ArticleRequest request) {
        store.article(id).orElseThrow(GuideService::articleNotFound);
        validArticle(request, id);
        store.updateArticle(articleRow(id, request));
        contentChanged(AuditAction.GUIDE_ARTICLE_UPDATED, AuditTargets.GUIDE_ARTICLE, id.toString(), request.code());
        return adminArticle(id);
    }

    @Transactional
    public void deleteArticle(UUID id) {
        GuideCustomRepository.ArticleRow row = store.article(id).orElseThrow(GuideService::articleNotFound);
        store.deleteArticle(id);
        contentChanged(AuditAction.GUIDE_ARTICLE_DELETED, AuditTargets.GUIDE_ARTICLE, id.toString(), row.code());
    }

    @Transactional(readOnly = true)
    public List<GuideAdmin.Pan> adminPans() {
        return store.systemPans().stream().map(this::adminPan).toList();
    }

    @Transactional
    public GuideAdmin.Pan createSystemPan(GuideAdmin.PanRequest request) {
        PanSizeRequest pan = validSystemPan(request, null);
        UUID id = UUID.randomUUID();
        store.upsertPan(new GuideCustomRepository.PanRow(id, request.code(), null, pan, request.translations(), order(request.displayOrder()), null));
        contentChanged(AuditAction.GUIDE_PAN_SIZE_CREATED, AuditTargets.GUIDE_PAN_SIZE, id.toString(), request.code());
        return adminPan(store.pan(id).orElseThrow());
    }

    @Transactional
    public GuideAdmin.Pan updateSystemPan(UUID id, GuideAdmin.PanRequest request) {
        systemPan(id);
        PanSizeRequest pan = validSystemPan(request, id);
        store.updatePan(new GuideCustomRepository.PanRow(id, request.code(), null, pan, request.translations(), order(request.displayOrder()), null));
        contentChanged(AuditAction.GUIDE_PAN_SIZE_UPDATED, AuditTargets.GUIDE_PAN_SIZE, id.toString(), request.code());
        return adminPan(store.pan(id).orElseThrow());
    }

    @Transactional
    public void deleteSystemPan(UUID id) {
        GuideCustomRepository.PanRow row = systemPan(id);
        store.deletePan(id, null);
        contentChanged(AuditAction.GUIDE_PAN_SIZE_DELETED, AuditTargets.GUIDE_PAN_SIZE, id.toString(), row.code());
    }

    @Transactional(readOnly = true)
    public List<GuideAdmin.Conversion> adminConversions() {
        return store.conversions().stream().map(GuideService::adminConversion).toList();
    }

    @Transactional
    public GuideAdmin.Conversion createConversion(GuideAdmin.ConversionRequest request) {
        validConversion(request);
        if (store.conversion(request.code()).isPresent()) {
            throw ApiException.of(ApiErrorCode.DUPLICATE_RESOURCE, "code", "kitchen.code.duplicate");
        }
        store.upsertConversion(conversionRow(request));
        contentChanged(AuditAction.GUIDE_CONVERSION_CREATED, AuditTargets.GUIDE_CONVERSION, request.code(), request.name());
        return adminConversion(store.conversion(request.code()).orElseThrow());
    }

    /** The code is the identity: a different {@code code} in the body is rejected. */
    @Transactional
    public GuideAdmin.Conversion updateConversion(String code, GuideAdmin.ConversionRequest request) {
        store.conversion(code).orElseThrow(GuideService::conversionNotFound);
        validConversion(request);
        if (!code.equals(request.code())) {
            throw ApiException.invalid("code", "kitchen.code.immutable");
        }
        store.upsertConversion(conversionRow(request));
        contentChanged(AuditAction.GUIDE_CONVERSION_UPDATED, AuditTargets.GUIDE_CONVERSION, code, request.name());
        return adminConversion(store.conversion(code).orElseThrow());
    }

    @Transactional
    public void deleteConversion(String code) {
        GuideCustomRepository.ConversionRow row = store.conversion(code).orElseThrow(GuideService::conversionNotFound);
        store.deleteConversion(code);
        contentChanged(AuditAction.GUIDE_CONVERSION_DELETED, AuditTargets.GUIDE_CONVERSION, code, row.conversion().name());
    }

    // ─── localisation ───────────────────────────────────────────────────────────────────

    static GuideArticle localized(GuideCustomRepository.ArticleRow row, GuideLanguage language) {
        Optional<GuideAdmin.ArticleTranslation> t = language.pick(row.translations());
        return new GuideArticle(row.id(), row.code(), row.category(),
                t.map(GuideAdmin.ArticleTranslation::title).filter(GuideService::present).orElse(row.title()),
                t.map(GuideAdmin.ArticleTranslation::summary).filter(GuideService::present).orElse(row.summary()), row.icon(),
                t.map(GuideAdmin.ArticleTranslation::tags).filter(tags -> !tags.isEmpty()).orElse(row.tags()),
                t.map(GuideAdmin.ArticleTranslation::blocks).filter(blocks -> !blocks.isEmpty()).orElse(row.blocks()), row.sources(),
                row.displayOrder());
    }

    static PanSize localized(GuideCustomRepository.PanRow row, GuideLanguage language) {
        Optional<GuideAdmin.PanTranslation> t = language.pick(row.translations());
        PanSizeRequest s = row.size();
        return new PanSize(row.id(), row.code(), row.scope(), s.shape(),
                t.map(GuideAdmin.PanTranslation::name).filter(GuideService::present).orElse(s.name()), s.diameterCm(), s.lengthCm(),
                s.widthCm(), s.heightCm(), s.volumeMl(), s.servings(),
                t.map(GuideAdmin.PanTranslation::notes).filter(GuideService::present).orElse(s.notes()));
    }

    static IngredientConversion localized(GuideCustomRepository.ConversionRow row, GuideLanguage language) {
        IngredientConversion c = row.conversion();
        String name = language.pick(row.translations()).map(GuideAdmin.ConversionTranslation::name).filter(GuideService::present).orElse(c.name());
        return new IngredientConversion(c.code(), name, c.gramsPerCup(), c.gramsPerTablespoon(), c.gramsPerTeaspoon());
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────

    private PanSizeRequest validUserPan(UUID owner, PanSizeRequest request, UUID excludeId) {
        Violations violations = new Violations();
        GuideValidator.pan(violations, request);
        violations.throwIfAny();
        PanSizeRequest pan = GuideValidator.normalized(request);
        if (store.userPanNameTaken(owner, pan.name(), excludeId)) {
            throw duplicatePan();
        }
        return pan;
    }

    private PanSizeRequest validSystemPan(GuideAdmin.PanRequest request, UUID excludeId) {
        Violations violations = new Violations();
        GuideValidator.panCode(violations, request.code());
        GuideValidator.pan(violations, request.dimensions());
        GuideValidator.panTranslations(violations, request.translations());
        if (request.code() != null && store.panCodeTaken(request.code(), excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "kitchen.code.duplicate");
        }
        violations.throwIfAny();
        return GuideValidator.normalized(request.dimensions());
    }

    private void validArticle(GuideAdmin.ArticleRequest request, UUID excludeId) {
        Violations violations = new Violations();
        GuideValidator.article(violations, request);
        if (request.code() != null && store.articleCodeTaken(request.code(), excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "kitchen.code.duplicate");
        }
        violations.throwIfAny();
    }

    private static void validConversion(GuideAdmin.ConversionRequest request) {
        Violations violations = new Violations();
        GuideValidator.conversion(violations, request);
        violations.throwIfAny();
    }

    private GuideCustomRepository.PanRow ownPan(UUID id, UUID owner) {
        GuideCustomRepository.PanRow row = store.pan(id).orElseThrow(GuideService::panNotFound);
        if (row.ownerId() == null) {
            throw ApiException.of(ApiErrorCode.ACCESS_DENIED, null, "kitchen.guide.systemPan");
        }
        if (!row.ownerId().equals(owner)) {
            throw panNotFound();
        }
        return row;
    }

    private GuideCustomRepository.PanRow systemPan(UUID id) {
        return store.pan(id).filter(row -> row.ownerId() == null).orElseThrow(GuideService::panNotFound);
    }

    /** Bumps the revision clients see and records the staff change. */
    private void contentChanged(AuditAction action, String targetType, String targetId, String label) {
        store.setRevision(TenantTime.today(clock));
        audit.record(AuditEvent.of(action, targetType, targetId, label).build());
    }

    private static GuideCustomRepository.ArticleRow articleRow(UUID id, GuideAdmin.ArticleRequest r) {
        return new GuideCustomRepository.ArticleRow(id, r.code(), r.category(), r.title().strip(), r.summary().strip(), r.icon(),
                r.tags().stream().map(String::strip).toList(), r.blocks(), r.sources().stream().map(String::strip).toList(), r.translations(),
                order(r.displayOrder()), r.isActive() == null || r.isActive(), null);
    }

    private static GuideCustomRepository.ConversionRow conversionRow(GuideAdmin.ConversionRequest r) {
        return new GuideCustomRepository.ConversionRow(new IngredientConversion(r.code(), r.name().strip(), r.gramsPerCup(),
                r.gramsPerTablespoon(), r.gramsPerTeaspoon()), r.translations(), order(r.displayOrder()), null);
    }

    private static GuideAdmin.Article admin(GuideCustomRepository.ArticleRow row) {
        return new GuideAdmin.Article(row.id(), row.code(), row.category(), row.title(), row.summary(), row.icon(), row.tags(), row.blocks(),
                row.sources(), row.displayOrder(), row.active(), row.translations(), row.updatedAt());
    }

    private GuideAdmin.Pan adminPan(GuideCustomRepository.PanRow row) {
        return new GuideAdmin.Pan(response(row), row.displayOrder(), row.translations(), row.updatedAt());
    }

    private static GuideAdmin.Conversion adminConversion(GuideCustomRepository.ConversionRow row) {
        return new GuideAdmin.Conversion(row.conversion(), row.displayOrder(), row.translations(), row.updatedAt());
    }

    private static PanSize response(GuideCustomRepository.PanRow row) {
        return localized(row, new GuideLanguage(List.of()));
    }

    static String userPanCode(UUID id) {
        return USER_PAN_CODE_PREFIX + id.toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static int order(Integer displayOrder) {
        return displayOrder == null ? 0 : displayOrder;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException duplicatePan() {
        return ApiException.of(ApiErrorCode.DUPLICATE_RESOURCE, "name", "kitchen.guide.panDuplicate");
    }

    private static ApiException panNotFound() {
        return ApiException.notFound("kitchen.guide.panNotFound");
    }

    private static ApiException articleNotFound() {
        return ApiException.notFound("kitchen.guide.articleNotFound");
    }

    private static ApiException conversionNotFound() {
        return ApiException.notFound("kitchen.guide.conversionNotFound");
    }
}
