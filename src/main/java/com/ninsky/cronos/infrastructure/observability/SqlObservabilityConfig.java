package com.ninsky.cronos.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Wraps the application {@link DataSource} in a datasource-proxy so every statement is counted and timed
 * ({@code Server-Timing: db;dur=…;desc="n stmts"}, metric {@code cronos.db.statement}). On in {@code dev} and in
 * tests ({@code app.observability.sql.enabled=true}); off by default elsewhere.
 * <p>
 * The proxy delegates {@code unwrap}, so Hikari pool metrics and health checks still see the Hikari pool.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.observability.sql", name = "enabled", havingValue = "true")
public class SqlObservabilityConfig {

    /** Static: a bean post-processor must not force early initialisation of this configuration. */
    @Bean
    static BeanPostProcessor sqlStatementDataSourceProxy(ObjectProvider<MeterRegistry> registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
                    return ProxyDataSourceBuilder.create(dataSource)
                            .name(beanName)
                            .listener(new SqlStatementListener(registry.getIfAvailable()))
                            .build();
                }
                return bean;
            }
        };
    }
}
