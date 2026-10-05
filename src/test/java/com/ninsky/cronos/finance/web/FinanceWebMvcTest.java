package com.ninsky.cronos.finance.web;

import com.ninsky.cronos.account.shared.api.AccountErrorMapper;
import com.ninsky.cronos.account.shared.api.AccountMessages;
import com.ninsky.cronos.finance.shared.FinanceMessages;
import com.ninsky.cronos.infrastructure.config.ValidationConfig;
import com.ninsky.cronos.infrastructure.config.security.SecurityConfig;
import com.ninsky.cronos.infrastructure.config.security.WebMvcConfig;
import com.ninsky.cronos.infrastructure.exception.StrictContractResponder;
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
 * Web slice for the finance controllers: real Bean Validation, Jackson, envelope advice,
 * {@code GlobalExceptionHandler} with the strict contract and method security; JWT filters are
 * replaced by a stateless test chain (anonymous → 401). Services are {@code @MockitoBean}s.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtAuthenticationFilter.class, JweFilter.class, RateLimitInterceptor.class, WebMvcConfig.class, SecurityConfig.class}))
@Import({AccountErrorMapper.class, AccountMessages.class, StrictContractResponder.class, ValidationConfig.class,
        FinanceMessages.class, FinanceWebMvcTestConfig.class})
public @interface FinanceWebMvcTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
