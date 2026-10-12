package com.ninsky.cronos.dashboard;

import com.ninsky.cronos.application.response.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard counters in one request (performance-latency §5.2); the UI called four endpoints before. */
@RestController
@RequestMapping("/dashboard")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Counters of the home screen")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "Recipes, quotes, ingredients and fixed costs of the caller",
            description = "Same scope as each module's stats; one SQL statement, cached 30 s per user.")
    public ResponseEntity<ApiResponse<DashboardSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.success(null, service.summary()));
    }
}
