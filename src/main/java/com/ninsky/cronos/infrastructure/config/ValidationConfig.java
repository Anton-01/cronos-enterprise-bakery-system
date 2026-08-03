package com.ninsky.cronos.infrastructure.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Wires Jakarta Bean Validation's {@code {key}} message interpolation to the app's
 * {@link MessageSource} (i18n/messages_en.properties, i18n/messages_es.properties) instead of
 * Hibernate Validator's default ValidationMessages.properties bundle. Setting
 * {@code validationMessageSource} also makes {@link LocalValidatorFactoryBean} resolve messages
 * against the current request's {@code LocaleContextHolder} locale automatically.
 */
@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean localValidatorFactoryBean(MessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }
}
