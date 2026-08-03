package com.ninsky.cronos.infrastructure.config.security;

import com.ninsky.cronos.infrastructure.util.auth.RateLimitInterceptor;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/auth/**", "/users/**");
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