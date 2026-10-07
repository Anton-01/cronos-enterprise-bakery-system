package com.ninsky.cronos.kitchen.shared;

import com.ninsky.cronos.kitchen.costing.CostEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Kitchen beans: the pure cost engine and module properties. */
@Configuration
@EnableConfigurationProperties(KitchenProperties.class)
public class KitchenConfig {

    @Bean
    public CostEngine costEngine() {
        return new CostEngine();
    }
}
