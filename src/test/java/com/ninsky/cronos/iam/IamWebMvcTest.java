package com.ninsky.cronos.iam;

import com.ninsky.cronos.account.shared.api.AccountErrorMapper;
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
 * Web slice for IAM controllers: real Jackson, envelope advice, {@code GlobalExceptionHandler} with the
 * strict responder and method security; JWT/JWE filters replaced by a stateless test chain (401 for anonymous).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtAuthenticationFilter.class, JweFilter.class, RateLimitInterceptor.class, WebMvcConfig.class, SecurityConfig.class}))
@Import({AccountErrorMapper.class, ValidationConfig.class, StrictContractResponder.class, IamWebMvcTestConfig.class})
public @interface IamWebMvcTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
