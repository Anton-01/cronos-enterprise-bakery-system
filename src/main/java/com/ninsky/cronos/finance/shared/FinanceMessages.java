package com.ninsky.cronos.finance.shared;

import com.ninsky.cronos.infrastructure.web.RequestLocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

/** Localised success messages for the envelope, in the caller's {@code Accept-Language}. */
@Component
@RequiredArgsConstructor
public class FinanceMessages {

    private final MessageSource messageSource;

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale());
    }

    private static Locale locale() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? RequestLocaleResolver.resolve(attributes.getRequest())
                : RequestLocaleResolver.DEFAULT_LOCALE;
    }
}
