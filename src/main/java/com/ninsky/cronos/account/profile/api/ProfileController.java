package com.ninsky.cronos.account.profile.api;

import com.ninsky.cronos.account.profile.application.GetMyProfileUseCase;
import com.ninsky.cronos.account.profile.application.UpdateMyProfileUseCase;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.profile.infrastructure.UserProfileMapper;
import com.ninsky.cronos.account.shared.api.AccountMessages;
import com.ninsky.cronos.account.shared.api.ETags;
import com.ninsky.cronos.account.shared.api.OpenApiExamples;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter: map, delegate, wrap. No business logic, and no user id anywhere in the API. */
@Validated
@RestController
@RequestMapping(value = "/users/me", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account Settings", description = "Self-service profile, avatar and fiscal data of the authenticated user")
public class ProfileController {

    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final UserProfileMapper userProfileMapper;
    private final AccountMessages messages;

    @GetMapping
    @Operation(summary = "Get my profile", description = "Profile of the authenticated user. The ETag header carries the version for If-Match.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile",
                    headers = @Header(name = HttpHeaders.ETAG, description = "Entity version, e.g. \"7\""),
                    content = @Content(examples = @ExampleObject(name = "profile", value = OpenApiExamples.USER_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile() {
        UserAccount account = getMyProfileUseCase.execute();
        return ResponseEntity.ok()
                .eTag(ETags.of(account.version()))
                .body(ApiResponse.success(messages.get("account.profile.retrieved"), userProfileMapper.toResponse(account)));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace my profile",
            description = "Full replace: every field is sent, null clears it. email/roles/enabled are not accepted (unknown properties → 400).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated profile",
                    content = @Content(examples = @ExampleObject(name = "profile", value = OpenApiExamples.USER_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(examples = @ExampleObject(name = "invalidPhone", value = OpenApiExamples.PHONE_VALIDATION_ERROR))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username taken (DUPLICATE_RESOURCE) or concurrent modification (SYSTEM_RESOURCE_CONFLICT)",
                    content = @Content(examples = @ExampleObject(name = "duplicateUsername", value = OpenApiExamples.DUPLICATE_USERNAME))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "412", description = "If-Match does not match the current version"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "More than 30 profile writes per hour")
    })
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @Parameter(in = ParameterIn.HEADER, description = "Optional optimistic-concurrency precondition (ETag from GET)")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserAccount updated = updateMyProfileUseCase.execute(userProfileMapper.toUpdate(request, ETags.parseIfMatch(ifMatch)));
        return ResponseEntity.ok()
                .eTag(ETags.of(updated.version()))
                .body(ApiResponse.success(messages.get("account.profile.updated"), userProfileMapper.toResponse(updated)));
    }
}
