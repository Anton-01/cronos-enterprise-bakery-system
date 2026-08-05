package com.ninsky.cronos.presentation.controller.user;

import com.ninsky.cronos.application.response.auth.LoginHistoryResponse;
import com.ninsky.cronos.application.response.auth.UserSessionResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.user.UserSecurityDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Security", description = "Endpoints for managing active sessions and login history")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/auth")
public class UserSecurityController {

    private final UserSecurityDataService userSecurityDataService;

    @GetMapping("/sessions")
    @Operation(summary = "Get active sessions", description = "Returns all active sessions for the current user")
    public ResponseEntity<ApiResponse<List<UserSessionResponse>>> getActiveSessions(Authentication authentication, HttpServletRequest request) {
        String currentIp = request.getHeader("X-Forwarded-For");
        if (currentIp == null || currentIp.isEmpty()) {
            currentIp = request.getRemoteAddr();
        }

        String currentUserAgent = request.getHeader("User-Agent");

        List<UserSessionResponse> sessions = userSecurityDataService.getActiveSessionsByUsername(authentication.getName(), currentIp, currentUserAgent);

        return ResponseEntity.ok(ApiResponse.success("Sesiones recuperadas", sessions));
    }

    @GetMapping("/login-history")
    @Operation(summary = "Get login history", description = "Returns the recent login attempts for the current user")
    public ResponseEntity<ApiResponse<List<LoginHistoryResponse>>> getLoginHistory(Authentication authentication) {
        List<LoginHistoryResponse> history = userSecurityDataService.getLoginHistoryByUsername(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Login history retrieved successfully", history));
    }
}