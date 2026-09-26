package com.ninsky.cronos.account.web;

import com.ninsky.cronos.account.avatar.infrastructure.AvatarUrlMapping;
import com.ninsky.cronos.account.fiscal.infrastructure.FiscalDataMapperImpl;
import com.ninsky.cronos.account.profile.infrastructure.UserProfileMapperImpl;
import com.ninsky.cronos.account.shared.api.AccountErrorMapper;
import com.ninsky.cronos.account.shared.api.AccountMessages;
import com.ninsky.cronos.account.shared.infrastructure.config.AccountInfrastructureConfig;
import com.ninsky.cronos.infrastructure.config.ValidationConfig;
import com.ninsky.cronos.infrastructure.config.security.SecurityConfig;
import com.ninsky.cronos.infrastructure.config.security.WebMvcConfig;
import com.ninsky.cronos.infrastructure.security.JwtAuthenticationFilter;
import com.ninsky.cronos.infrastructure.util.auth.RateLimitInterceptor;
import com.ninsky.cronos.infrastructure.web.JweFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Web slice for the account controllers: real validation, Jackson (incl. strict unknown-property
 * handling), MapStruct mappers, envelope advice and {@code GlobalExceptionHandler}; the JWT/JWE
 * filters and the IP rate-limit interceptor are replaced by Spring Boot's default test security
 * (so "unauthenticated → 401" is still exercised). Use cases are {@code @MockitoBean}s per test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtAuthenticationFilter.class, JweFilter.class, RateLimitInterceptor.class, WebMvcConfig.class, SecurityConfig.class}))
@Import({AccountErrorMapper.class, AccountMessages.class, AccountInfrastructureConfig.class, ValidationConfig.class,
        AccountWebMvcTestConfig.class, UserProfileMapperImpl.class, FiscalDataMapperImpl.class, AvatarUrlMapping.class})
public @interface AccountWebMvcTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
