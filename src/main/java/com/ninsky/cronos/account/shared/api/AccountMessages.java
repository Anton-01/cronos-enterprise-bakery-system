package com.ninsky.cronos.account.shared.api;

import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

/**
 * Localized envelope {@code message}s. Resolves the locale exactly like the error path
 * ({@link RequestLocaleResolver}: {@code en*} → English, anything else → Spanish) so success and
 * error messages of one request always agree.
 */
@Component
@RequiredArgsConstructor
public class AccountMessages {

    private final MessageSource messageSource;

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, currentLocale());
    }

    public Locale currentLocale() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return RequestLocaleResolver.resolve(attributes.getRequest());
        }
        return RequestLocaleResolver.DEFAULT_LOCALE;
    }
}
