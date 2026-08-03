package com.ninsky.cronos.infrastructure.config;

import com.ninsky.cronos.domain.service.core.RawMaterialCostingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers pure domain services (no Spring/JPA dependencies of their own, see
 * {@code domain/service/**}) as beans so application-layer services can depend on them the same
 * way as everything else, via constructor injection.
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public RawMaterialCostingService rawMaterialCostingService() {
        return new RawMaterialCostingService();
    }
}
