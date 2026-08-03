package com.ninsky.cronos.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${app.cache.unit-types.ttl:60}")
    private int ttl;

    @Value("${app.cache.unit-types.max-size:500}")
    private int maxSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineConfig());
        return cacheManager;
    }

    Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.MINUTES) // Expira a los X minutos de escribirse
                .maximumSize(maxSize) // Evita que la caché crezca indefinidamente
                .recordStats(); // Útil para monitorear hits/misses en logs si lo necesitas
    }
}
