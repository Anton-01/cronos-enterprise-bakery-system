package com.ninsky.cronos.iam.permission;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Code-defined permission catalog, localised (spec §5). */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/iam/permissions")
@Tag(name = "IAM · Permissions", description = "Read-only permission catalog")
public class PermissionController {

    private final MessageSource messages;

    @GetMapping
    @PreAuthorize(Authorities.PERMISSION_CATALOG_READ)
    @Operation(summary = "Every permission in canonical order")
    public ResponseEntity<ApiResponse<List<PermissionView>>> list() {
        Locale locale = LocaleContextHolder.getLocale();
        List<PermissionView> views = PermissionCatalog.all().stream().map(d -> new PermissionView(d.code(), d.module(), d.resource(),
                d.action(), text("permission.module." + d.module(), locale),
                text("permission.resource." + d.module() + "." + d.resource(), locale),
                text("permission." + d.code() + ".name", locale), text("permission." + d.code() + ".description", locale),
                d.risk(), d.dependsOn().stream().sorted().toList())).toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePrivate())
                .header("Vary", "Accept-Language")
                .body(ApiResponse.success(views));
    }

    private String text(String key, Locale locale) {
        return messages.getMessage(key, null, key, locale);
    }
}
