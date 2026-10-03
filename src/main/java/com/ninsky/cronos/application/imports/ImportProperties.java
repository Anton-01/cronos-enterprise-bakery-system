package com.ninsky.cronos.application.imports;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * {@code app.imports.*}. {@code maxFileSize} must not exceed
 * {@code spring.servlet.multipart.max-file-size}, which rejects larger uploads (413) before they
 * ever reach the import service.
 */
@ConfigurationProperties("app.imports")
public record ImportProperties(
        @DefaultValue("2MB") DataSize maxFileSize,
        @DefaultValue("2000") int maxRows
) {
}
