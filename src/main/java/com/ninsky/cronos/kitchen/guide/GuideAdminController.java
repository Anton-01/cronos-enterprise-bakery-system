package com.ninsky.cronos.kitchen.guide;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.kitchen.shared.KitchenAccess;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform staff maintain the SYSTEM guide content without a migration (baking-studio §6.3). Every write is audited. */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/baking-guide/admin")
@PreAuthorize(KitchenAccess.GUIDE_CONTENT_MANAGE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Kitchen · Baker's guide (staff)", description = "SYSTEM articles, pan sizes and conversions, with translations")
public class GuideAdminController {

    private final GuideService service;
    private final KitchenMessages messages;

    @GetMapping("/articles")
    @Operation(summary = "All SYSTEM articles (inactive included) with their translations")
    public ResponseEntity<ApiResponse<List<GuideAdmin.Article>>> articles() {
        return ResponseEntity.ok(ApiResponse.success(null, service.adminArticles()));
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "One SYSTEM article")
    public ResponseEntity<ApiResponse<GuideAdmin.Article>> article(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, service.adminArticle(id)));
    }

    @PostMapping("/articles")
    @Operation(summary = "Create an article", description = "Blocks are structured data; HTML-looking text is rejected.")
    public ResponseEntity<ApiResponse<GuideAdmin.Article>> createArticle(@RequestBody GuideAdmin.ArticleRequest request) {
        GuideAdmin.Article created = service.createArticle(request);
        return ResponseEntity.status(201).body(ApiResponse.success(messages.get("kitchen.guide.saved"), created));
    }

    @PutMapping("/articles/{id}")
    @Operation(summary = "Replace an article")
    public ResponseEntity<ApiResponse<GuideAdmin.Article>> updateArticle(@PathVariable UUID id, @RequestBody GuideAdmin.ArticleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.saved"), service.updateArticle(id, request)));
    }

    @DeleteMapping("/articles/{id}")
    @Operation(summary = "Delete an article")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(@PathVariable UUID id) {
        service.deleteArticle(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.deleted"), null));
    }

    @GetMapping("/pan-sizes")
    @Operation(summary = "SYSTEM pan sizes with their translations")
    public ResponseEntity<ApiResponse<List<GuideAdmin.Pan>>> pans() {
        return ResponseEntity.ok(ApiResponse.success(null, service.adminPans()));
    }

    @PostMapping("/pan-sizes")
    @Operation(summary = "Create a SYSTEM pan size")
    public ResponseEntity<ApiResponse<GuideAdmin.Pan>> createPan(@RequestBody GuideAdmin.PanRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(messages.get("kitchen.guide.saved"), service.createSystemPan(request)));
    }

    @PutMapping("/pan-sizes/{id}")
    @Operation(summary = "Replace a SYSTEM pan size")
    public ResponseEntity<ApiResponse<GuideAdmin.Pan>> updatePan(@PathVariable UUID id, @RequestBody GuideAdmin.PanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.saved"), service.updateSystemPan(id, request)));
    }

    @DeleteMapping("/pan-sizes/{id}")
    @Operation(summary = "Delete a SYSTEM pan size")
    public ResponseEntity<ApiResponse<Void>> deletePan(@PathVariable UUID id) {
        service.deleteSystemPan(id);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.deleted"), null));
    }

    @GetMapping("/conversions")
    @Operation(summary = "Volume conversions with their translations")
    public ResponseEntity<ApiResponse<List<GuideAdmin.Conversion>>> conversions() {
        return ResponseEntity.ok(ApiResponse.success(null, service.adminConversions()));
    }

    @PostMapping("/conversions")
    @Operation(summary = "Create a conversion")
    public ResponseEntity<ApiResponse<GuideAdmin.Conversion>> createConversion(@RequestBody GuideAdmin.ConversionRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(messages.get("kitchen.guide.saved"), service.createConversion(request)));
    }

    @PutMapping("/conversions/{code}")
    @Operation(summary = "Replace a conversion", description = "The code is immutable.")
    public ResponseEntity<ApiResponse<GuideAdmin.Conversion>> updateConversion(@PathVariable String code,
                                                                               @RequestBody GuideAdmin.ConversionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.saved"), service.updateConversion(code, request)));
    }

    @DeleteMapping("/conversions/{code}")
    @Operation(summary = "Delete a conversion")
    public ResponseEntity<ApiResponse<Void>> deleteConversion(@PathVariable String code) {
        service.deleteConversion(code);
        return ResponseEntity.ok(ApiResponse.success(messages.get("kitchen.guide.deleted"), null));
    }
}
