package com.ninsky.cronos.iam.sod;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** Read-only SoD rules (spec §3.7). */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/iam/sod-rules")
@Tag(name = "IAM · SoD rules", description = "Segregation-of-duties rules")
public class SodRuleController {

    private final SodRuleCustomRepository rules;

    /** Localised rule. */
    public record SodRuleView(String code, String name, String description, SodSeverity severity, List<List<String>> permissionSets) {
    }

    @GetMapping
    @PreAuthorize(Authorities.IAM_ROLE_READ)
    @Operation(summary = "Active SoD rules")
    public ResponseEntity<ApiResponse<List<SodRuleView>>> list() {
        Locale locale = LocaleContextHolder.getLocale();
        return ResponseEntity.ok(ApiResponse.success(rules.findActive().stream()
                .map(r -> new SodRuleView(r.code(), r.name(locale), r.description(locale), r.severity(),
                        r.permissionSets().stream().map(set -> set.stream().sorted().toList()).toList()))
                .toList()));
    }
}
