package com.ninsky.cronos.account.shared.infrastructure.config;

import com.ninsky.cronos.account.avatar.infrastructure.AvatarProperties;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.account.shared.infrastructure.json.StrictUnknownPropertyHandler;
import com.ninsky.cronos.account.shared.infrastructure.ratelimit.AccountRateLimitProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AccountRateLimitProperties.class, AvatarProperties.class})
public class AccountInfrastructureConfig {

    @Bean
    public PiiMasker piiMasker() {
        return new PiiMasker();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictAccountDtoDeserialization() {
        return builder -> builder.postConfigurer(objectMapper -> objectMapper.addHandler(new StrictUnknownPropertyHandler()));
    }
}
