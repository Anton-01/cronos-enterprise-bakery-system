package com.ninsky.cronos.infrastructure.config.security;

import com.ninsky.cronos.infrastructure.util.auth.RateLimitInterceptor;
import com.ninsky.cronos.infrastructure.web.deprecation.LegacyAdminDeprecationInterceptor;
import com.ninsky.cronos.infrastructure.web.deprecation.LegacyKitchenDeprecationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final Validator localValidatorFactoryBean;
    private final LegacyAdminDeprecationInterceptor legacyAdminDeprecation;
    private final LegacyKitchenDeprecationInterceptor legacyKitchenDeprecation;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/auth/**", "/users/**");
        registry.addInterceptor(legacyAdminDeprecation)
                .addPathPatterns("/admin/users/**", "/admin/roles/**", "/admin/audit-log/**");
        registry.addInterceptor(legacyKitchenDeprecation)
                .addPathPatterns(LegacyKitchenDeprecationInterceptor.PATHS);
    }

    /**
     * Without this override, Spring MVC builds its own throwaway validator for
     * {@code @Valid @RequestBody} arguments instead of reusing the bean wired to the app's
     * bilingual {@link org.springframework.context.MessageSource} in {@code ValidationConfig},
     * silently breaking {@code {key}} message interpolation.
     */
    @Override
    public Validator getValidator() {
        return localValidatorFactoryBean;
    }
}