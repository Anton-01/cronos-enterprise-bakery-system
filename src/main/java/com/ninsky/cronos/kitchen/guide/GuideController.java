package com.ninsky.cronos.kitchen.guide;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.kitchen.shared.KitchenAccess;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.UUID;

@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/baking-guide")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Kitchen · Baker's guide", description = "Food safety, techniques, costing rules, pan sizes and conversions (baking-studio §6)")
public class GuideController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate();

    private final GuideService service;
    private final KitchenMessages messages;

    @GetMapping
    @PreAuthorize(KitchenAccess.GUIDE_READ)
    @Operation(summary = "The whole guide", description = "SYSTEM articles and conversions, SYSTEM then own pans; localised by Accept-Language "
            + "(exact tag, language, es-MX). Honours If-None-Match (304).")
    public ResponseEntity<ApiResponse<BakingGuide>> guide(@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
                                                          WebRequest request) {
        GuideLanguage language = GuideLanguage.of(acceptLanguage);
        String etag = service.etag(language);
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CACHE).varyBy(HttpHeaders.ACCEPT_LANGUAGE).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CACHE).varyBy(HttpHeaders.ACCEPT_LANGUAGE)
                .body(ApiResponse.success(null, service.guide(language)));
    }

    @PostMapping("/pan-sizes")
    @PreAuthorize(KitchenAccess.GUIDE_PAN_MANAGE)
    @Operation(summary = "Add one of my pans", description = "Up to 50; names are unique per user (case-insensitive).")
    public ResponseEntity<ApiResponse<PanSize>> createPan(@RequestBody PanSizeRequest request) {
        PanSize created = service.createPan(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.success(messages.get("kitchen.guide.panCreated", created.name()), created));
    }

    @PutMapping("/pan-sizes/{id}")
    @PreAuthorize(KitchenAccess.GUIDE_PAN_MANAGE)
    @Operation(summary = "Edit one of my pans", description = "SYSTEM pans → 403; another user's → 404.")
    public ResponseEntity<ApiResponse<PanSize>> updatePan(@PathVariable UUID id, @RequestBody PanSizeRequest request) {
        PanSize updated = service.updatePan(id, request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.panUpdated", updated.name()), updated));
    }

    @DeleteMapping("/pan-sizes/{id}")
    @PreAuthorize(KitchenAccess.GUIDE_PAN_MANAGE)
    @Operation(summary = "Delete one of my pans")
    public ResponseEntity<ApiResponse<Void>> deletePan(@PathVariable UUID id) {
        service.deletePan(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.panDeleted"), null));
    }
}
