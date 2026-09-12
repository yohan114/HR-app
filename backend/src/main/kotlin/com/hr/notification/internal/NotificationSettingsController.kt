package com.hr.notification.internal

import com.hr.identity.Caller
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A user's own notification settings.
 *
 * Under `/v1/me` and with no permission check, because the subject is the token holder. There is
 * deliberately no endpoint here for reading or writing somebody *else's* preferences: an
 * administrator who can silence an employee's notifications can hide an approval request from
 * them, and that is a capability worth introducing on purpose rather than as a side effect of a
 * settings screen.
 */
@RestController
@RequestMapping("/v1/me/notification-settings")
@PreAuthorize("isAuthenticated()")
class NotificationSettingsController(
    private val service: NotificationSettingsService,
) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal jwt: Jwt,
    ): NotificationSettings = service.settingsFor(Caller.from(jwt).userId)

    @PutMapping
    fun replace(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: NotificationSettings,
    ): NotificationSettings = service.replace(Caller.from(jwt).userId, request)
}
