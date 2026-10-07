package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.api.AccessPreview;
import com.ninsky.cronos.iam.user.api.AccessRequest;
import com.ninsky.cronos.iam.user.api.AvatarChanged;
import com.ninsky.cronos.iam.user.api.BulkResult;
import com.ninsky.cronos.iam.user.api.BulkRolesRequest;
import com.ninsky.cronos.iam.user.api.BulkStatusRequest;
import com.ninsky.cronos.iam.user.api.CreateIamUserRequest;
import com.ninsky.cronos.iam.user.api.IamUserDetail;
import com.ninsky.cronos.iam.user.api.IamUserSummary;
import com.ninsky.cronos.iam.user.api.LoginAttempt;
import com.ninsky.cronos.iam.user.api.PasswordResetIssued;
import com.ninsky.cronos.iam.user.api.PasswordResetRequest;
import com.ninsky.cronos.iam.user.api.ReasonRequest;
import com.ninsky.cronos.iam.user.api.SessionsRevoked;
import com.ninsky.cronos.iam.user.api.UpdateIamUserRequest;
import com.ninsky.cronos.iam.user.api.UserAccessView;
import com.ninsky.cronos.iam.user.api.UserAvailability;
import com.ninsky.cronos.iam.user.api.IamUserSession;
import com.ninsky.cronos.iam.user.api.UserStats;
import com.ninsky.cronos.iam.user.api.UserStatusRequest;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** IAM users (spec §3). */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/iam/users")
@Tag(name = "IAM · Users", description = "User administration: profile, lifecycle, credentials, access, sessions")
public class UserController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final IamUserService users;
    private final UserStatusService statuses;
    private final UserCredentialService credentials;
    private final UserAccessEditor access;
    private final UserSessionService sessions;
    private final UserAvatarService avatars;
    private final UserExportService exports;
    private final Clock clock;

    @GetMapping
    @PreAuthorize(Authorities.IAM_USER_READ)
    @Operation(summary = "Paged, filtered user list")
    public ResponseEntity<ApiResponse<CatalogPage<IamUserSummary>>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String search,
            @RequestParam(required = false) List<UserStatus> statuses,
            @RequestParam(name = "statuses[]", required = false) List<UserStatus> statusesArray,
            @RequestParam(required = false) List<Long> roleIds,
            @RequestParam(name = "roleIds[]", required = false) List<Long> roleIdsArray,
            @RequestParam(required = false) Boolean twoFactorEnabled) {
        UserSearch filter = new UserSearch(search, merge(statuses, statusesArray), merge(roleIds, roleIdsArray), twoFactorEnabled);
        PageQuery query = PageQuery.of(page, size, sort, UserReadCustomRepository.SORTS, UserReadCustomRepository.DEFAULT_SORT);
        return ResponseEntity.ok(ApiResponse.success(users.list(filter, query)));
    }

    @GetMapping("/stats")
    @PreAuthorize(Authorities.IAM_USER_READ)
    @Operation(summary = "User counters by status, 2FA, dormancy and expiry")
    public ResponseEntity<ApiResponse<UserStats>> stats() {
        return ResponseEntity.ok(ApiResponse.success(users.stats()));
    }

    @GetMapping("/availability")
    @PreAuthorize(Authorities.IAM_USER_READ)
    @Operation(summary = "Whether a username / email is free (30 req/min)")
    public ResponseEntity<ApiResponse<UserAvailability>> availability(@RequestParam(required = false) String username,
                                                                      @RequestParam(required = false) String email,
                                                                      @RequestParam(required = false) UUID excludeId) {
        return ResponseEntity.ok(ApiResponse.success(users.availability(username, email, excludeId)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize(Authorities.IAM_USER_EXPORT)
    @Operation(summary = "CSV export of the filtered list (5 per 10 min)")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String sort, @RequestParam(required = false) String search,
            @RequestParam(required = false) List<UserStatus> statuses,
            @RequestParam(name = "statuses[]", required = false) List<UserStatus> statusesArray,
            @RequestParam(required = false) List<Long> roleIds,
            @RequestParam(name = "roleIds[]", required = false) List<Long> roleIdsArray,
            @RequestParam(required = false) Boolean twoFactorEnabled) {
        UserSearch filter = new UserSearch(search, merge(statuses, statusesArray), merge(roleIds, roleIdsArray), twoFactorEnabled);
        PageQuery order = PageQuery.of(0, 1, sort, UserReadCustomRepository.SORTS, UserReadCustomRepository.DEFAULT_SORT);
        String fileName = "usuarios-" + TenantTime.today(clock) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(exports.export(filter, order));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Authorities.IAM_USER_CREATE)
    @Operation(summary = "Create a user (invitation or temporary password), optional avatar")
    public ResponseEntity<ApiResponse<IamUserDetail>> create(@RequestPart("user") CreateIamUserRequest request,
                                                             @RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        IamUserDetail created = users.create(request, bytes(avatar, "avatar"), LocaleContextHolder.getLocale());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath().path("/iam/users/{id}")
                        .buildAndExpand(created.summary().id()).toUri())
                .body(ApiResponse.success(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorities.IAM_USER_READ)
    @Operation(summary = "User detail")
    public ResponseEntity<ApiResponse<IamUserDetail>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(users.detail(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.IAM_USER_UPDATE)
    @Operation(summary = "Replace profile fields")
    public ResponseEntity<ApiResponse<IamUserDetail>> update(@PathVariable UUID id, @RequestBody UpdateIamUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(users.update(id, request)));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(Authorities.IAM_USER_CHANGE_STATUS)
    @Operation(summary = "Move the user along the lifecycle")
    public ResponseEntity<ApiResponse<IamUserDetail>> changeStatus(@PathVariable UUID id, @RequestBody UserStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(statuses.change(id, request)));
    }

    @PutMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Authorities.IAM_USER_UPDATE)
    @Operation(summary = "Replace the user's avatar")
    public ResponseEntity<ApiResponse<AvatarChanged>> replaceAvatar(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(avatars.replace(id, bytes(file, "file"))));
    }

    @DeleteMapping("/{id}/avatar")
    @PreAuthorize(Authorities.IAM_USER_UPDATE)
    @Operation(summary = "Remove the user's avatar (idempotent)")
    public ResponseEntity<ApiResponse<Void>> removeAvatar(@PathVariable UUID id) {
        avatars.remove(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize(Authorities.IAM_USER_RESET_CREDENTIALS)
    @Operation(summary = "Issue a reset link or a temporary password (5/hour per user)")
    public ResponseEntity<ApiResponse<PasswordResetIssued>> resetPassword(@PathVariable UUID id,
                                                                          @RequestBody PasswordResetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(credentials.resetPassword(id, request)));
    }

    @PostMapping("/{id}/require-password-change")
    @PreAuthorize(Authorities.IAM_USER_RESET_CREDENTIALS)
    @Operation(summary = "Force a password change at next login")
    public ResponseEntity<ApiResponse<IamUserDetail>> requirePasswordChange(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(credentials.requirePasswordChange(id)));
    }

    @PostMapping("/{id}/two-factor/reset")
    @PreAuthorize(Authorities.IAM_USER_RESET_CREDENTIALS)
    @Operation(summary = "Remove the user's TOTP enrolment")
    public ResponseEntity<ApiResponse<IamUserDetail>> resetTwoFactor(@PathVariable UUID id, @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success(credentials.resetTwoFactor(id, request.reason())));
    }

    @PostMapping("/{id}/invitation/resend")
    @PreAuthorize(Authorities.IAM_USER_RESET_CREDENTIALS)
    @Operation(summary = "Send a fresh invitation (5/hour per user)")
    public ResponseEntity<ApiResponse<IamUserDetail>> resendInvitation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(credentials.resendInvitation(id)));
    }

    @GetMapping("/{id}/access")
    @PreAuthorize(Authorities.IAM_USER_READ)
    @Operation(summary = "Roles, groups, overrides, effective permissions and SoD conflicts")
    public ResponseEntity<ApiResponse<UserAccessView>> getAccess(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(access.get(id, LocaleContextHolder.getLocale())));
    }

    @PostMapping("/{id}/access/preview")
    @PreAuthorize(Authorities.IAM_USER_MANAGE_ACCESS)
    @Operation(summary = "Effect of a proposed assignment; nothing persisted (60/min)")
    public ResponseEntity<ApiResponse<AccessPreview>> previewAccess(@PathVariable UUID id, @RequestBody AccessRequest request) {
        return ResponseEntity.ok(ApiResponse.success(access.preview(id, request, LocaleContextHolder.getLocale())));
    }

    @PutMapping("/{id}/access")
    @PreAuthorize(Authorities.IAM_USER_MANAGE_ACCESS)
    @Operation(summary = "Replace roles, groups, grants and denials")
    public ResponseEntity<ApiResponse<UserAccessView>> saveAccess(@PathVariable UUID id, @RequestBody AccessRequest request) {
        return ResponseEntity.ok(ApiResponse.success(access.save(id, request, LocaleContextHolder.getLocale())));
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize(Authorities.IAM_USER_MANAGE_SESSIONS)
    @Operation(summary = "Active sessions")
    public ResponseEntity<ApiResponse<List<IamUserSession>>> sessions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(sessions.sessions(id)));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize(Authorities.IAM_USER_MANAGE_SESSIONS)
    @Operation(summary = "End one session")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        sessions.revoke(id, sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize(Authorities.IAM_USER_MANAGE_SESSIONS)
    @Operation(summary = "End every session")
    public ResponseEntity<ApiResponse<SessionsRevoked>> revokeSessions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(sessions.revokeAll(id)));
    }

    @GetMapping("/{id}/login-history")
    @PreAuthorize(Authorities.IAM_USER_MANAGE_SESSIONS)
    @Operation(summary = "Sign-in attempts, newest first")
    public ResponseEntity<ApiResponse<CatalogPage<LoginAttempt>>> loginHistory(@PathVariable UUID id,
                                                                              @RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(sessions.loginHistory(id, page, size, LocaleContextHolder.getLocale())));
    }

    @PostMapping("/bulk/status")
    @PreAuthorize(Authorities.IAM_USER_CHANGE_STATUS)
    @Operation(summary = "Change the status of up to 200 users")
    public ResponseEntity<ApiResponse<BulkResult>> bulkStatus(@RequestBody BulkStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(statuses.changeMany(request)));
    }

    @PostMapping("/bulk/roles")
    @PreAuthorize(Authorities.IAM_USER_MANAGE_ACCESS)
    @Operation(summary = "Add and remove roles on up to 200 users")
    public ResponseEntity<ApiResponse<BulkResult>> bulkRoles(@RequestBody BulkRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(access.changeRoles(request, LocaleContextHolder.getLocale())));
    }

    private static <T> List<T> merge(List<T> plain, List<T> bracketed) {
        return Stream.of(plain, bracketed).filter(Objects::nonNull).flatMap(List::stream).distinct().toList();
    }

    private static byte[] bytes(MultipartFile file, String field) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.invalid(field, "account.avatar.file.required");
        }
    }
}
