package com.ninsky.cronos.account.fiscal.infrastructure;

import com.ninsky.cronos.account.fiscal.application.port.ZipCodeCatalog;
import com.ninsky.cronos.account.fiscal.domain.MxZipCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default {@link ZipCodeCatalog}: every well-formed code is accepted. Declare another
 * {@code ZipCodeCatalog} bean (e.g. backed by SAT's published c_CodigoPostal) to replace it.
 */
@Configuration
public class NoOpZipCodeCatalog {

    @Bean
    @ConditionalOnMissingBean(ZipCodeCatalog.class)
    public ZipCodeCatalog acceptAllZipCodeCatalog() {
        return (MxZipCode zipCode) -> true;
    }
}
