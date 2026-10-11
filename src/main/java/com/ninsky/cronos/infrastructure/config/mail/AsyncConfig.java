package com.ninsky.cronos.infrastructure.config.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * Background work no longer runs in {@code @Async} listeners: every side effect goes through the transactional outbox.
 * The decorator is applied by Spring Boot to the application task executor (MVC async requests such as the
 * notification stream), so the MDC and the trace context follow the work.
 */
@Configuration
public class AsyncConfig {

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
