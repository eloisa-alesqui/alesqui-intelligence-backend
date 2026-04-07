package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.dto.dashboard.DashboardSummaryResponse;
import es.alesqui.intelligence.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller that exposes the user dashboard endpoints.
 *
 * Provides a summary endpoint that aggregates profile data, activity metrics,
 * visible APIs, and role-specific sections for the authenticated caller.
 * All endpoints require an authenticated principal; no additional role
 * restrictions are applied at the controller level.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Returns the dashboard summary for the currently authenticated user.
     *
     * Delegates entirely to DashboardService, which resolves the caller's
     * identity from the security context and fans out to the relevant domain
     * services to assemble the response.
     *
     * @return a Mono emitting the DashboardSummaryResponse for the caller.
     */
    @GetMapping("/summary")
    public Mono<DashboardSummaryResponse> getSummary() {
        log.debug("GET /api/dashboard/summary requested");
        return dashboardService.getSummary();
    }
}
