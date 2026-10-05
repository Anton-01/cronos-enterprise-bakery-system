package com.ninsky.cronos.iam.policy.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.policy.SecurityPolicyRequest;
import com.ninsky.cronos.iam.policy.SecurityPolicyService;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Singleton security policy (spec §8). */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/iam/security-policy")
@Tag(name = "IAM · Security policy", description = "Password, lockout, session and 2FA rules")
public class SecurityPolicyController {

    private final SecurityPolicyService policies;
    private final MessageSource messages;

    @GetMapping
    @PreAuthorize(Authorities.IAM_SECURITY_POLICY_READ)
    @Operation(summary = "Current security policy")
    public ResponseEntity<ApiResponse<SecurityPolicyView>> get() {
        return ResponseEntity.ok(ApiResponse.success(policies.get()));
    }

    @PutMapping
    @PreAuthorize(Authorities.IAM_SECURITY_POLICY_UPDATE)
    @Operation(summary = "Replace the security policy",
            description = "All fields required; stale version → 409 CONCURRENT_MODIFICATION. Weakening a control is audited as WARNING.")
    public ResponseEntity<ApiResponse<SecurityPolicyView>> update(@RequestBody SecurityPolicyRequest request) {
        SecurityPolicyView updated = policies.update(request);
        return ResponseEntity.ok(ApiResponse.success(
                messages.getMessage("security.policy.updated", null, LocaleContextHolder.getLocale()), updated));
    }
}
