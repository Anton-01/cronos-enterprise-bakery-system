package com.ninsky.cronos.account.avatar.api;

import com.ninsky.cronos.account.avatar.application.RemoveAvatarUseCase;
import com.ninsky.cronos.account.avatar.application.UploadAvatarUseCase;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.profile.infrastructure.UserProfileMapper;
import com.ninsky.cronos.account.shared.api.AccountMessages;
import com.ninsky.cronos.account.shared.api.ETags;
import com.ninsky.cronos.account.shared.api.OpenApiExamples;
import com.ninsky.cronos.application.response.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(value = "/users/me/avatar", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account Settings")
public class AvatarController {

    private final UploadAvatarUseCase uploadAvatarUseCase;
    private final RemoveAvatarUseCase removeAvatarUseCase;
    private final UserProfileMapper userProfileMapper;
    private final AccountMessages messages;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload my avatar",
            description = "multipart/form-data with a single part `file` (≤ 2 MB, JPEG/PNG/WebP by magic bytes, shorter edge ≥ 128 px). "
                    + "Re-encoded server-side to a ≤ 512×512 JPEG without metadata; the returned URL changes whenever the image does.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Avatar stored",
                    content = @Content(examples = @ExampleObject(name = "avatar", value = OpenApiExamples.AVATAR_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Corrupt, too small or too many pixels (field `file`)",
                    content = @Content(examples = @ExampleObject(name = "tooSmall", value = OpenApiExamples.AVATAR_TOO_SMALL))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Larger than 2 MB"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "Not a JPEG/PNG/WebP"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "More than 10 uploads per hour")
    })
    public ResponseEntity<ApiResponse<AvatarResponse>> uploadAvatar(
            @Parameter(in = ParameterIn.HEADER, description = "Optional optimistic-concurrency precondition (ETag from GET /users/me)")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestPart("file") MultipartFile file) throws IOException {
        UserAccount updated = uploadAvatarUseCase.execute(file.getBytes(), ETags.parseIfMatch(ifMatch));
        return ResponseEntity.ok()
                .eTag(ETags.of(updated.version()))
                .body(ApiResponse.success(messages.get("account.avatar.updated"), userProfileMapper.toAvatarResponse(updated)));
    }

    @DeleteMapping
    @Operation(summary = "Remove my avatar", description = "Idempotent: 200 with data null even when there is no avatar.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Avatar removed",
            content = @Content(examples = @ExampleObject(name = "removed", value = OpenApiExamples.NULL_DATA)))
    public ResponseEntity<ApiResponse<Void>> removeAvatar() {
        removeAvatarUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(messages.get("account.avatar.removed"), null));
    }
}
