package com.ninsky.cronos.iam.twofactor.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.twofactor.TwoFactorAccountService;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Contract §8.2: self-service TOTP of the signed-in user. Authentication only, no IAM permission;
 * every response is {@code Cache-Control: no-store} because several carry secrets.
 */
@RestController
@StrictApiContract
@RequestMapping("/users/me/two-factor")
@Tag(name = "Account · Two-factor", description = "TOTP enrolment, recovery codes and disabling, for the signed-in user")
public class TwoFactorController {

    private final TwoFactorAccountService twoFactor;
    private final ActorProvider actors;

    public TwoFactorController(TwoFactorAccountService twoFactor, ActorProvider actors) {
        this.twoFactor = twoFactor;
        this.actors = actors;
    }

    @GetMapping
    @Operation(summary = "My 2FA status", description = "Reachable while the enrolment gate blocks everything else.")
    public ResponseEntity<ApiResponse<TwoFactorStatus>> status() {
        return noStore(() -> twoFactor.status(me()));
    }

    @PostMapping("/enrollment")
    @Operation(summary = "Start TOTP enrolment",
            description = "New secret, otpauth URI and server-rendered QR; valid 10 minutes and replaces earlier pending ones. "
                    + "409 TWO_FACTOR_ALREADY_ENABLED; 429 after 10 per hour.")
    public ResponseEntity<ApiResponse<TwoFactorEnrollment>> startEnrollment() {
        return noStore(() -> twoFactor.startEnrollment(me()));
    }

    @PostMapping("/enrollment/confirm")
    @Operation(summary = "Confirm enrolment with a current code",
            description = "Turns 2FA on and returns 10 recovery codes once. 400 INVALID_TOTP_CODE, 404 unknown or used enrolment, "
                    + "409 ENROLLMENT_EXPIRED. The 5th wrong code consumes the enrolment.")
    public ResponseEntity<ApiResponse<TwoFactorRecoveryCodes>> confirm(@RequestBody ConfirmEnrollmentRequest request) {
        return noStore(() -> twoFactor.confirm(me(), request));
    }

    @PostMapping("/disable")
    @Operation(summary = "Turn 2FA off",
            description = "Needs the password and a TOTP or recovery code. 409 TWO_FACTOR_REQUIRED_BY_ROLE when 2FA is mandatory. "
                    + "Signs out every other session.")
    public ResponseEntity<ApiResponse<TwoFactorStatus>> disable(@RequestBody DisableTwoFactorRequest request) {
        return noStore(() -> twoFactor.disable(me(), request));
    }

    @PostMapping("/recovery-codes")
    @Operation(summary = "Replace my recovery codes", description = "Needs a TOTP or recovery code; old codes stop working at once.")
    public ResponseEntity<ApiResponse<TwoFactorRecoveryCodes>> regenerate(@RequestBody RecoveryCodesRequest request) {
        return noStore(() -> twoFactor.regenerateRecoveryCodes(me(), request));
    }

    private UUID me() {
        return actors.require().id();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(Supplier<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body.get()));
    }
}
