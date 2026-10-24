package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.mail.domain.EffectiveContent;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.TemplateDefaults;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository.OverrideRow;
import com.ninsky.cronos.mail.infrastructure.EmailSettingsCustomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Effective email data through per-instance Caffeine caches (§5.1). The effective template is read on every outgoing
 * email and every catalog load while writes are rare, so reads are cached aggressively and evicted precisely:
 * <ul>
 *   <li>{@code email.effective} {@code (tenant, key, locale)} → override or default;</li>
 *   <li>{@code email.settings} {@code tenant} → brand and sender;</li>
 *   <li>{@code email.catalog} {@code (tenant, language)} → the catalog response and its ETag;</li>
 *   <li>{@code email.compiled} {@code (tenant, key, locale, contentRevision, settingsRevision)} → render plan.</li>
 * </ul>
 * Evictions run after commit, never before (a concurrent read would re-cache the old row): on this instance through
 * {@link #afterCommit}, on every other one through the outbox fan-out ({@link #evict}, idempotent).
 */
@Component
public class EmailContentProvider {

    public static final String EFFECTIVE = "email.effective";
    public static final String SETTINGS = "email.settings";
    public static final String CATALOG = "email.catalog";
    public static final String COMPILED = "email.compiled";

    /** {@code email.effective} key. */
    public record EffectiveKey(UUID tenantId, String templateKey, EmailLocale locale) {
    }

    /** {@code email.catalog} key. */
    public record CatalogKey(UUID tenantId, EmailLocale language) {
    }

    /** {@code email.compiled} key. */
    public record CompiledKey(UUID tenantId, String templateKey, EmailLocale locale, Long contentRevision, Long settingsRevision) {
    }

    private final TemplateDefaults defaults;
    private final EmailOverrideCustomRepository overrides;
    private final EmailSettingsCustomRepository settings;
    private final MailProperties properties;
    private final CacheManager caches;
    private final String frontendUrl;

    public EmailContentProvider(TemplateDefaults defaults, EmailOverrideCustomRepository overrides, EmailSettingsCustomRepository settings,
                                MailProperties properties, CacheManager caches, @Value("${app.frontend.url}") String frontendUrl) {
        this.defaults = defaults;
        this.overrides = overrides;
        this.settings = settings;
        this.properties = properties;
        this.caches = caches;
        this.frontendUrl = frontendUrl;
    }

    /** The tenant every request resolves to. */
    public UUID tenant() {
        return properties.tenantId();
    }

    /** The built-in definition. */
    public Optional<TemplateDefinition> definition(String key) {
        return defaults.find(key);
    }

    /**
     * The built-in definition.
     *
     * @throws ApiException EMAIL_TEMPLATE_NOT_FOUND
     */
    public TemplateDefinition requireDefinition(String key) {
        return definition(key).orElseThrow(() -> ApiException.of(ApiErrorCode.EMAIL_TEMPLATE_NOT_FOUND, null, "mail.template.notFound", key));
    }

    public TemplateDefaults defaults() {
        return defaults;
    }

    /** Override ?? default of {@code (key, locale)}. */
    public EffectiveContent effective(UUID tenant, TemplateDefinition template, EmailLocale locale) {
        return cached(EFFECTIVE, new EffectiveKey(tenant, template.key(), locale), () -> overrides.find(tenant, template.key(), locale)
                .map(EmailContentProvider::effective)
                .orElseGet(() -> EffectiveContent.ofDefault(template, locale)));
    }

    /** The saved settings, or the {@code app.mail.*} defaults (revision null) until the first save. */
    public EmailSettings settings(UUID tenant) {
        return cached(SETTINGS, tenant, () -> settings.find(tenant).orElseGet(this::defaultSettings));
    }

    /** {@code app.mail.*} defaults. */
    public EmailSettings defaultSettings() {
        return properties.defaultSettings(frontendUrl);
    }

    /** Render plan of {@code (key, locale)}: no database read once the three caches are warm. */
    public CompiledTemplate compiled(UUID tenant, TemplateDefinition template, EmailLocale locale) {
        EffectiveContent content = effective(tenant, template, locale);
        EmailSettings current = settings(tenant);
        return cached(COMPILED, new CompiledKey(tenant, template.key(), locale, content.revision(), current.revision()),
                () -> new CompiledTemplate(template, content, current));
    }

    /** Every override of the tenant, loaded once per catalog build (and used to warm {@code email.effective}). */
    public List<EffectiveContent> overrides(UUID tenant) {
        List<EffectiveContent> loaded = overrides.findAll(tenant).stream().map(EmailContentProvider::effective).toList();
        Cache effective = cache(EFFECTIVE);
        loaded.forEach(content -> effective.putIfAbsent(new EffectiveKey(tenant, content.templateKey(), content.locale()), content));
        return loaded;
    }

    /** {@code email.catalog} read-through. */
    public <T> T catalog(UUID tenant, EmailLocale language, Supplier<T> loader) {
        return cached(CATALOG, new CatalogKey(tenant, language), loader);
    }

    /** Local eviction once the writing transaction committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(EmailCacheEviction eviction) {
        evict(eviction);
    }

    /**
     * Applies {@code eviction} to this instance's caches (idempotent):
     * content → {@code effective(tenant, key, locale)}, {@code catalog(tenant, *)}, {@code compiled(tenant, key, locale, *)};
     * settings → {@code settings(tenant)}, {@code catalog(tenant, *)}, {@code compiled(tenant, *)}.
     */
    public void evict(EmailCacheEviction eviction) {
        UUID tenant = eviction.tenantId();
        removeIf(CATALOG, key -> key instanceof CatalogKey k && k.tenantId().equals(tenant));
        if (eviction.settingsChange()) {
            cache(SETTINGS).evict(tenant);
            removeIf(COMPILED, key -> key instanceof CompiledKey k && k.tenantId().equals(tenant));
        } else {
            cache(EFFECTIVE).evict(new EffectiveKey(tenant, eviction.templateKey(), eviction.locale()));
            removeIf(COMPILED, key -> key instanceof CompiledKey k && k.tenantId().equals(tenant)
                    && k.templateKey().equals(eviction.templateKey()) && k.locale() == eviction.locale());
        }
    }

    private static EffectiveContent effective(OverrideRow row) {
        return new EffectiveContent(row.templateKey(), row.locale(), row.revision(), row.content(), row.updatedAt(), row.updatedBy());
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String name, Object key, Supplier<T> loader) {
        Cache cache = cache(name);
        Cache.ValueWrapper hit = cache.get(key);
        if (hit != null && hit.get() != null) {
            return (T) hit.get();
        }
        T loaded = loader.get();
        cache.put(key, loaded);
        return loaded;
    }

    @SuppressWarnings("unchecked")
    private void removeIf(String name, Predicate<Object> matches) {
        Object nativeCache = cache(name).getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeine) {
            ((com.github.benmanes.caffeine.cache.Cache<Object, Object>) caffeine).asMap().keySet().removeIf(matches);
        } else {
            cache(name).clear();
        }
    }

    private Cache cache(String name) {
        return Objects.requireNonNull(caches.getCache(name), "Cache " + name + " is not configured");
    }
}
