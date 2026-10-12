package com.ninsky.cronos.identity.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.user.api.BulkResult;
import com.ninsky.cronos.identity.application.RegistrationReviewService;
import com.ninsky.cronos.identity.application.RegistrationService;
import com.ninsky.cronos.identity.domain.RegistrationStatus;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/** Review queue of self-registered accounts (§3.2). */
@RestController
@StrictApiContract
@RequestMapping("/iam/registrations")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "IAM · Registrations", description = "Approve (with roles) or reject self-registered accounts")
public class RegistrationReviewController {

    private final RegistrationReviewService reviews;
    private final RegistrationService registrations;

    public RegistrationReviewController(RegistrationReviewService reviews, RegistrationService registrations) {
        this.reviews = reviews;
        this.registrations = registrations;
    }

    @GetMapping
    @PreAuthorize(Authorities.IAM_REGISTRATION_READ)
    @Operation(summary = "Search registrations",
            description = "Sort whitelist: submittedAt, displayName, email, status, reviewedAt (default submittedAt,asc).")
    public ResponseEntity<ApiResponse<CatalogPage<RegistrationSummary>>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String search,
            @RequestParam(required = false) Set<RegistrationStatus> statuses, @RequestParam(required = false) Boolean emailVerified) {
        return ResponseEntity.ok(ApiResponse.success(null, reviews.page(search, statuses, emailVerified, page, size, sort)));
    }

    @GetMapping("/stats")
    @PreAuthorize(Authorities.IAM_REGISTRATION_READ)
    @Operation(summary = "Queue figures")
    public ResponseEntity<ApiResponse<RegistrationStats>> stats() {
        return ResponseEntity.ok(ApiResponse.success(null, reviews.stats()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorities.IAM_REGISTRATION_READ)
    @Operation(summary = "One registration")
    public ResponseEntity<ApiResponse<RegistrationSummary>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(null, reviews.get(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(Authorities.IAM_REGISTRATION_DECIDE)
    @Operation(summary = "Approve with roles", description = "PENDING only; the account becomes ACTIVE. "
            + "An unverified email is allowed, with a warning in message.")
    public ResponseEntity<ApiResponse<RegistrationSummary>> approve(@PathVariable UUID id,
                                                                    @Valid @RequestBody ApproveRegistrationRequest request) {
        RegistrationReviewService.Decision decision = reviews.approve(id, request);
        return ResponseEntity.ok(ApiResponse.success(decision.warning(), decision.registration()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(Authorities.IAM_REGISTRATION_DECIDE)
    @Operation(summary = "Reject with a reason", description = "PENDING only; the account is deactivated.")
    public ResponseEntity<ApiResponse<RegistrationSummary>> reject(@PathVariable UUID id,
                                                                   @Valid @RequestBody RejectRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, reviews.reject(id, request).registration()));
    }

    @PostMapping("/{id}/resend-verification")
    @PreAuthorize(Authorities.IAM_REGISTRATION_DECIDE)
    @Operation(summary = "Resend the verification link to the applicant")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@PathVariable UUID id) {
        registrations.resendVerificationFor(reviews.accountOf(id));
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/bulk")
    @PreAuthorize(Authorities.IAM_REGISTRATION_DECIDE)
    @Operation(summary = "Decide up to 100 registrations", description = "Each one in its own transaction; partial success.")
    public ResponseEntity<ApiResponse<BulkResult>> bulk(@Valid @RequestBody BulkRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, reviews.bulk(request)));
    }
}
