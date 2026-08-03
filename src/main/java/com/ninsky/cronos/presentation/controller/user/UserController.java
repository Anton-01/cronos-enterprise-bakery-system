package com.ninsky.cronos.presentation.controller.user;

import com.ninsky.cronos.application.request.user.UpdateProfileRequest;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.auth.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Profile", description = "Endpoints for user profile management")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Returns the profile of the authenticated user")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        UserResponse response = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", response));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Updates personal and business information")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse currentUser = userService.getUserByUsername(authentication.getName());
        UserResponse updatedProfile = userService.updateUserProfile(currentUser.id(), request);

        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updatedProfile));
    }
}
