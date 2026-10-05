package com.ninsky.cronos.iam.group;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.iam.group.api.PermissionGroupDetail;
import com.ninsky.cronos.iam.group.api.PermissionGroupRequest;
import com.ninsky.cronos.iam.group.api.PermissionGroupSummary;
import com.ninsky.cronos.iam.permission.Authorities;
import com.ninsky.cronos.iam.role.api.StatusRequest;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Permission groups (spec §6). */
@RestController
@StrictApiContract
@RequiredArgsConstructor
@RequestMapping("/iam/permission-groups")
@Tag(name = "IAM · Permission groups", description = "Reusable permission bundles")
public class PermissionGroupController {

    private final PermissionGroupService groups;

    @GetMapping
    @PreAuthorize(Authorities.IAM_PERMISSION_GROUP_READ)
    @Operation(summary = "List groups sorted by name")
    public ResponseEntity<ApiResponse<List<PermissionGroupSummary>>> list() {
        return ResponseEntity.ok(ApiResponse.success(groups.list()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorities.IAM_PERMISSION_GROUP_READ)
    @Operation(summary = "Group detail")
    public ResponseEntity<ApiResponse<PermissionGroupDetail>> get(@PathVariable long id) {
        return ResponseEntity.ok(ApiResponse.success(groups.detail(id)));
    }

    @PostMapping
    @PreAuthorize(Authorities.IAM_PERMISSION_GROUP_CREATE)
    @Operation(summary = "Create a group")
    public ResponseEntity<ApiResponse<PermissionGroupDetail>> create(@RequestBody PermissionGroupRequest request) {
        PermissionGroupDetail created = groups.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/iam/permission-groups/{id}").buildAndExpand(created.summary().id()).toUri())
                .body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.IAM_PERMISSION_GROUP_UPDATE)
    @Operation(summary = "Update a group")
    public ResponseEntity<ApiResponse<PermissionGroupDetail>> update(@PathVariable long id, @RequestBody PermissionGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(groups.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Authorities.IAM_PERMISSION_GROUP_UPDATE)
    @Operation(summary = "Activate or deactivate a group")
    public ResponseEntity<ApiResponse<PermissionGroupDetail>> changeStatus(@PathVariable long id, @RequestBody StatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(groups.changeStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorities.IAM_PERMISSION_GROUP_DELETE)
    @Operation(summary = "Delete an unused group")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id) {
        groups.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
