package com.ninsky.cronos.presentation.controller.core.auth;

import com.ninsky.cronos.application.request.core.auth.ForgotPasswordRequest;
import com.ninsky.cronos.application.request.core.auth.ResetPasswordRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.auth.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Password Reset", description = "Public endpoints for password recovery")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends a recovery email if the account exists")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {

        // Ejecutamos la lógica. Incluso si el correo no existe, devolvemos 200 OK
        // para evitar ataques de enumeración de usuarios (User Enumeration).
        passwordResetService.processForgotPassword(request.email());

        return ResponseEntity.ok(ApiResponse.success(
                "If the email is registered, a recovery link has been sent.", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Sets a new password using a valid token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPasswordWithToken(request.token(), request.newPassword());

        return ResponseEntity.ok(ApiResponse.success(
                "Password has been reset successfully. You can now login.", null));
    }
}
