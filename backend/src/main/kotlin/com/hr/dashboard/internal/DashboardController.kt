package com.hr.dashboard.internal

import com.hr.dashboard.DashboardResponse
import com.hr.identity.Caller
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Composite dashboard endpoint.
 *
 * Implements Phase C composite dashboard for role-adaptive widget aggregation.
 */
@RestController
@RequestMapping("/v1/dashboard")
@PreAuthorize("isAuthenticated()")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    @GetMapping
    fun getDashboard(
        @AuthenticationPrincipal jwt: Jwt,
    ): DashboardResponse {
        val caller = Caller.from(jwt)
        return dashboardService.getDashboard(caller)
    }
}
