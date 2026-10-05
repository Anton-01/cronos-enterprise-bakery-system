package com.ninsky.cronos.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
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
        // Access data must converge across instances quickly (spec §1.4.5: ≤ 30 s).
        cacheManager.registerCustomCache("accessVersion", shortLived(Duration.ofSeconds(30), 10_000));
        cacheManager.registerCustomCache("effectiveAccess", shortLived(Duration.ofMinutes(10), 5_000));
        cacheManager.registerCustomCache("userAuth", shortLived(Duration.ofSeconds(30), 10_000));
        cacheManager.registerCustomCache("sodRules", shortLived(Duration.ofMinutes(5), 10));
        cacheManager.registerCustomCache("securityPolicy", shortLived(Duration.ofSeconds(30), 10));
        cacheManager.registerCustomCache("financeSettings", shortLived(Duration.ofSeconds(30), 10));
        return cacheManager;
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> shortLived(Duration ttl, long maxSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).recordStats().build();
    }

    Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.MINUTES) // Expira a los X minutos de escribirse
                .maximumSize(maxSize) // Evita que la caché crezca indefinidamente
                .recordStats(); // Útil para monitorear hits/misses en logs si lo necesitas
    }
}
