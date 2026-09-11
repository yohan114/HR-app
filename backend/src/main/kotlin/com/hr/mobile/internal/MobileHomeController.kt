package com.hr.mobile.internal

import com.hr.identity.Caller
import com.hr.mobile.MobileHomeResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Mobile home composite endpoint.
 *
 * Implements P1-BE-25: GET /v1/mobile/home.
 */
@RestController
@RequestMapping("/v1/mobile/home")
class MobileHomeController(
    private val homeCompositeService: HomeCompositeService,
) {
    @GetMapping
    fun home(
        @AuthenticationPrincipal jwt: Jwt,
    ): MobileHomeResponse {
        val caller = Caller.from(jwt)
        val roles = jwt.getClaimAsStringList("roles")?.toSet() ?: emptySet()
        return homeCompositeService.buildHomeScreen(caller, roles)
    }
}
