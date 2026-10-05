package com.ninsky.cronos.finance.pricing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes the pure calculator as a bean. */
@Configuration
public class PricingConfig {

    @Bean
    public PricingCalculator pricingCalculator() {
        return new PricingCalculator();
    }
}
