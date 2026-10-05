package com.ninsky.cronos.iam.role;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.role.api.CloneRoleRequest;
import com.ninsky.cronos.iam.role.api.IamRoleDetail;
import com.ninsky.cronos.iam.role.api.IamRoleSummary;
import com.ninsky.cronos.iam.role.api.MembersAdded;
import com.ninsky.cronos.iam.role.api.MembersRemoved;
import com.ninsky.cronos.iam.role.api.MembersRequest;
import com.ninsky.cronos.iam.role.api.IamRoleRequest;
import com.ninsky.cronos.iam.role.api.StatusRequest;
import com.ninsky.cronos.iam.user.UserReadRepository;
import com.ninsky.cronos.iam.user.api.IamUserSummary;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Roles (spec §4.3). */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/iam/roles")
@Tag(name = "IAM · Roles", description = "Roles, their permissions, groups and members")
public class RoleController {

    private final RoleService roles;

    @GetMapping
    @PreAuthorize(Authorities.IAM_ROLE_READ)
    @Operation(summary = "List roles sorted by name")
    public ResponseEntity<ApiResponse<List<IamRoleSummary>>> list(@RequestParam(required = false) String search,
                                                                 @RequestParam(required = false) RoleStatus status) {
        return ResponseEntity.ok(ApiResponse.success(roles.list(search, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorities.IAM_ROLE_READ)
    @Operation(summary = "Role detail with effective permissions and SoD conflicts")
    public ResponseEntity<ApiResponse<IamRoleDetail>> get(@PathVariable long id) {
        return ResponseEntity.ok(ApiResponse.success(roles.detail(id, LocaleContextHolder.getLocale())));
    }

    @PostMapping
    @PreAuthorize(Authorities.IAM_ROLE_CREATE)
    @Operation(summary = "Create a role")
    public ResponseEntity<ApiResponse<IamRoleDetail>> create(@RequestBody IamRoleRequest request) {
        IamRoleDetail created = roles.create(request, LocaleContextHolder.getLocale());
        return ResponseEntity.created(location(created.summary().id())).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.IAM_ROLE_UPDATE)
    @Operation(summary = "Update a role")
    public ResponseEntity<ApiResponse<IamRoleDetail>> update(@PathVariable long id, @RequestBody IamRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roles.update(id, request, LocaleContextHolder.getLocale())));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Authorities.IAM_ROLE_UPDATE)
    @Operation(summary = "Activate or deactivate a role")
    public ResponseEntity<ApiResponse<IamRoleDetail>> changeStatus(@PathVariable long id, @RequestBody StatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roles.changeStatus(id, request, LocaleContextHolder.getLocale())));
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize(Authorities.IAM_ROLE_CREATE)
    @Operation(summary = "Clone a role without its members")
    public ResponseEntity<ApiResponse<IamRoleDetail>> cloneRole(@PathVariable long id, @RequestBody CloneRoleRequest request) {
        IamRoleDetail created = roles.cloneRole(id, request, LocaleContextHolder.getLocale());
        return ResponseEntity.created(location(created.summary().id())).body(ApiResponse.success(created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorities.IAM_ROLE_DELETE)
    @Operation(summary = "Delete an unused, non-system role")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id) {
        roles.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize(Authorities.IAM_ROLE_READ)
    @Operation(summary = "Paged members of a role")
    public ResponseEntity<ApiResponse<CatalogPage<IamUserSummary>>> members(@PathVariable long id,
                                                                           @RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size,
                                                                           @RequestParam(required = false) String search) {
        PageQuery query = PageQuery.of(page, size, null, UserReadRepository.SORTS, "displayName,asc");
        return ResponseEntity.ok(ApiResponse.success(roles.members(id, search, query)));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize(Authorities.IAM_ROLE_MANAGE_MEMBERS)
    @Operation(summary = "Add members (idempotent per user)")
    public ResponseEntity<ApiResponse<MembersAdded>> addMembers(@PathVariable long id, @RequestBody MembersRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roles.addMembers(id, request, LocaleContextHolder.getLocale())));
    }

    @PostMapping("/{id}/members/remove")
    @PreAuthorize(Authorities.IAM_ROLE_MANAGE_MEMBERS)
    @Operation(summary = "Remove members")
    public ResponseEntity<ApiResponse<MembersRemoved>> removeMembers(@PathVariable long id, @RequestBody MembersRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roles.removeMembers(id, request, LocaleContextHolder.getLocale())));
    }

    private static URI location(long id) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/iam/roles/{id}").buildAndExpand(id).toUri();
    }
}
