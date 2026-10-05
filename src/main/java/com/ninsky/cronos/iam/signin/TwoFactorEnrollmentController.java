package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.application.request.core.auth.VerifyTwoFactorRequest;
import com.ninsky.cronos.application.response.auth.TwoFactorSetupResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.auth.UserService;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service TOTP enrolment; reachable while the 2FA gate blocks everything else. */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/users/me/two-factor")
@Tag(name = "Account · Two-factor", description = "TOTP enrolment of the signed-in user")
public class TwoFactorEnrollmentController {

    private final UserService users;
    private final ActorProvider actors;
    private final MessageSource messages;

    @PostMapping("/setup")
    @Operation(summary = "Start TOTP enrolment",
            description = "Returns the otpauth URI (QR) and the secret for manual entry, shown once to the account owner only.")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup() {
        return ResponseEntity.ok(ApiResponse.success(users.setupTwoFactor(actors.require().id())));
    }

    @PostMapping("/enable")
    @Operation(summary = "Confirm TOTP enrolment with a current 6-digit code")
    public ResponseEntity<ApiResponse<Void>> enable(@Valid @RequestBody VerifyTwoFactorRequest request) {
        users.enableTwoFactor(actors.require().id(), request);
        return ResponseEntity.ok(ApiResponse.success(
                messages.getMessage("security.twoFactor.enabled", null, LocaleContextHolder.getLocale()), null));
    }
}
