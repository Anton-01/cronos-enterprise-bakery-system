package com.ninsky.cronos.infrastructure.config.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Standalone RedissonClient for distributed locks (e.g. RLock around concurrent recipe/quote
 * writes), wired to the same Redis instance as spring.data.redis.* on purpose: introducing a
 * second, independently-configured Redis connection here would make host/port drift between the
 * two a silent failure mode instead of a config error.
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://%s:%d".formatted(redisHost, redisPort))
                .setPassword(redisPassword.isBlank() ? null : redisPassword)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10);
        return Redisson.create(config);
    }
}
