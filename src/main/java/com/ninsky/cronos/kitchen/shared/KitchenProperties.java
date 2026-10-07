package com.ninsky.cronos.kitchen.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** {@code app.kitchen.*}: limits and thresholds of the kitchen module. */
@ConfigurationProperties("app.kitchen")
public record KitchenProperties(
        @DefaultValue("60") int stalePriceDays,
        @DefaultValue("500") int synchronousRippleLimit,
        @DefaultValue("120") int costPreviewsPerMinute,
        @DefaultValue("25MB") DataSize maxFileSize,
        @DefaultValue("40") int maxFilesPerRecipe,
        @DefaultValue("500MB") DataSize tenantFileQuota,
        @DefaultValue("15") int signedUrlMinutes,
        @DefaultValue("30") int filePurgeAfterDays,
        @DefaultValue("") String demoTenant
) {
}
