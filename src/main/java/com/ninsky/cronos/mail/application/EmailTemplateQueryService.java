package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.mail.api.EmailTemplateCatalogResponse;
import com.ninsky.cronos.mail.api.EmailTemplateContent;
import com.ninsky.cronos.mail.api.EmailTemplateHistoryEntry;
import com.ninsky.cronos.mail.api.EmailTemplateResponse;
import com.ninsky.cronos.mail.domain.EffectiveContent;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository.HistoryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads of the email template screens: the catalog (effective content, brand, sender) with its ETag, one template, and the
 * history of a locale. The catalog is built from {@code email.catalog}; a cold build costs one query for the overrides
 * and one for the authors' names.
 */
@Service
public class EmailTemplateQueryService {

    /** The catalog and its weak ETag ({@code W/"<sha256>"}), cached together. */
    public record CatalogSnapshot(EmailTemplateCatalogResponse catalog, String etag) {
    }

    private final EmailContentProvider provider;
    private final EmailOverrideCustomRepository overrides;

    public EmailTemplateQueryService(EmailContentProvider provider, EmailOverrideCustomRepository overrides) {
        this.provider = provider;
        this.overrides = overrides;
    }

    /**
     * {@code GET /}: served from {@code email.catalog}; {@code acceptLanguage} is normalised to {@code es-MX} / {@code en}.
     * No transaction: a cache hit must not even check out a connection.
     */
    public CatalogSnapshot catalog(String acceptLanguage) {
        UUID tenant = provider.tenant();
        EmailLocale language = EmailLocale.normalize(acceptLanguage);
        return provider.catalog(tenant, language, () -> build(tenant, language));
    }

    /** One template with its current effective content, read from the database (used as the answer of a write). */
    @Transactional(readOnly = true)
    public EmailTemplateResponse template(String key) {
        TemplateDefinition definition = provider.requireDefinition(key);
        UUID tenant = provider.tenant();
        List<EffectiveContent> current = Arrays.stream(EmailLocale.values())
                .map(locale -> overrides.find(tenant, key, locale)
                        .map(row -> new EffectiveContent(row.templateKey(), row.locale(), row.revision(), row.content(), row.updatedAt(),
                                row.updatedBy())))
                .flatMap(Optional::stream)
                .toList();
        return response(definition, current, names(current.stream().map(EffectiveContent::updatedBy).toList()));
    }

    /** History of {@code (key, locale)}, newest first. */
    @Transactional(readOnly = true)
    public CatalogPage<EmailTemplateHistoryEntry> history(String key, EmailLocale locale, int page, int size) {
        provider.requireDefinition(key);
        CatalogPage<HistoryRow> rows = overrides.historyPage(provider.tenant(), key, locale, Math.max(0, page), Math.clamp(size, 1, 100));
        Map<UUID, String> names = names(rows.content().stream().map(HistoryRow::changedBy).toList());
        List<EmailTemplateHistoryEntry> entries = rows.content().stream().map(row -> new EmailTemplateHistoryEntry(row.revision(),
                row.content() == null ? null : row.content().subject(), row.content() == null ? null : row.content().preheader(),
                row.content() == null ? null : row.content().blocks(), row.changedAt(), names.get(row.changedBy()), row.action())).toList();
        return new CatalogPage<>(entries, rows.pageNumber(), rows.pageSize(), rows.totalElements(), rows.totalPages(), rows.last());
    }

    private CatalogSnapshot build(UUID tenant, EmailLocale language) {
        EmailSettings settings = provider.settings(tenant);
        Map<String, List<EffectiveContent>> byTemplate = provider.overrides(tenant).stream()
                .collect(Collectors.groupingBy(EffectiveContent::templateKey));
        Map<UUID, String> names = names(byTemplate.values().stream().flatMap(List::stream).map(EffectiveContent::updatedBy).toList());
        List<EmailTemplateResponse> templates = provider.defaults().templates().stream()
                .map(definition -> response(definition, byTemplate.getOrDefault(definition.key(), List.of()), names))
                .toList();
        EmailTemplateCatalogResponse catalog = new EmailTemplateCatalogResponse(provider.defaults().version(), settings.brand(),
                settings.sender(), settings.revision(), templates);
        return new CatalogSnapshot(catalog, etag(catalog, byTemplate.values().stream().flatMap(List::stream).toList(), language));
    }

    private static EmailTemplateResponse response(TemplateDefinition definition, List<EffectiveContent> customized, Map<UUID, String> names) {
        Map<EmailLocale, EffectiveContent> byLocale = customized.stream()
                .collect(Collectors.toMap(EffectiveContent::locale, c -> c, (a, b) -> a, () -> new EnumMap<>(EmailLocale.class)));
        Map<EmailLocale, EmailTemplateContent> content = new EnumMap<>(EmailLocale.class);
        for (EmailLocale locale : EmailLocale.values()) {
            EffectiveContent effective = byLocale.getOrDefault(locale, EffectiveContent.ofDefault(definition, locale));
            content.put(locale, EmailTemplateContent.of(effective.revision(), effective.content()));
        }
        Optional<EffectiveContent> latest = byLocale.values().stream().max(Comparator.comparing(EffectiveContent::updatedAt));
        return new EmailTemplateResponse(definition.key(), definition.category(), definition.audience(), definition.trigger(),
                definition.transactional(), definition.description(), definition.variables(), content, List.copyOf(byLocale.keySet()),
                latest.map(EffectiveContent::updatedAt).orElse(null), latest.map(c -> names.get(c.updatedBy())).orElse(null));
    }

    /**
     * {@code W/"sha256(defaultsVersion | settingsRevision | overrides | language)"}. The overrides part lists every
     * {@code key:locale:revision}, so any save, reset or settings change yields a new tag (a bare max/count could repeat).
     */
    static String etag(EmailTemplateCatalogResponse catalog, Collection<EffectiveContent> customized, EmailLocale language) {
        String overridesPart = customized.stream().map(c -> c.templateKey() + ":" + c.locale().tag() + ":" + c.revision()).sorted()
                .collect(Collectors.joining(","));
        String source = String.join("|", catalog.version(), String.valueOf(catalog.settingsRevision()), overridesPart, language.tag());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return "W/\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        return overrides.displayNames(ids.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet()));
    }
}
