package com.ninsky.cronos.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect @Component @Slf4j
public class CacheMonitoringAspect {

    // Este punto de corte intercepta cualquier método anotado con @Cacheable
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object monitorCache(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        log.debug("Cache check | method: {}", methodName);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // Ejecuta el método
        // Si el resultado ya está en caché, Spring NO ejecutará este bloque
        // Si el resultado NO está en caché (Cache MISS), se ejecutará el código del Service
        Object result = joinPoint.proceed();

        stopWatch.stop();

        long executionTime = stopWatch.getTotalTimeMillis();

        if (executionTime > 5) { // Si tarda más de 5ms, probablemente fue a la DB (MISS)
            log.debug("Cache miss (probably) | method '{}' took {} ms", methodName, executionTime);
        } else {
            log.debug("Cache hit (probably) | method '{}' took {} ms", methodName, executionTime);
        }
        return result;
    }
}
