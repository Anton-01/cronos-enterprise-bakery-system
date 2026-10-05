package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public invitation acceptance. */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AccountActivationController {

    /** Raw invitation token from the emailed link and the chosen password. */
    public record ActivationRequest(String token, String password) {
        @Override
        public String toString() {
            return "ActivationRequest[***]";
        }
    }

    private final ActivationService activation;
    private final MessageSource messages;

    @PostMapping("/activate")
    @Operation(summary = "Accept an invitation", description = "Sets the first password (security policy applies) and activates the account. "
            + "An invalid, used or expired token is a 400 on field token.")
    public ResponseEntity<ApiResponse<Void>> activate(@RequestBody ActivationRequest request) {
        activation.activate(request.token(), request.password());
        return ResponseEntity.ok(ApiResponse.success(messages.getMessage("security.activation.done", null, LocaleContextHolder.getLocale()), null));
    }
}
