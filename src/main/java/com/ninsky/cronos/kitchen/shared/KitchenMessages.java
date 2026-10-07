package com.ninsky.cronos.kitchen.shared;

import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

/** Localised texts for envelopes, warnings and revision summaries. */
@Component
@RequiredArgsConstructor
public class KitchenMessages {

    private final MessageSource messageSource;

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, KitchenLocale.current());
    }

    public String get(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    /** {@code es} or {@code en}: the i18n row locale of the current request. */
    public static String language() {
        return KitchenLocale.language(KitchenLocale.current());
    }

    /** Request locale helpers (outside a request: Spanish). */
    public static final class KitchenLocale {

        private KitchenLocale() {
        }

        public static Locale current() {
            return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                    ? RequestLocaleResolver.resolve(attributes.getRequest())
                    : RequestLocaleResolver.DEFAULT_LOCALE;
        }

        public static String language(Locale locale) {
            return Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? "en" : "es";
        }
    }
}
