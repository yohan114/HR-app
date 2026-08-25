package com.hr.identity.internal

import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import com.hr.shared.api.UnauthenticatedException
import com.hr.tenancy.TenantContext
import com.nimbusds.jose.jwk.RSAKey
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Authentication endpoints.
 *
 * Every endpoint here is reached before, or without, a bearer token, so the tenant is supplied by
 * the `X-Tenant-Code` header and resolved by `TenantFilter`. That includes refresh and biometric:
 * the token lookup is itself tenant-scoped by row-level security, so we must know the tenant
 * before we can find the token.
 */
@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authenticationService: AuthenticationService,
    private val deviceService: DeviceService,
    private val rsaKey: RSAKey,
) {
    @PostMapping("/token")
    fun signIn(
        @Valid @RequestBody request: PasswordGrantRequest,
        @RequestHeader(value = "User-Agent", required = false) userAgent: String?,
    ): TokenResponse = authenticationService.signInWithPassword(request, userAgent)

    @PostMapping("/token/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
        @RequestHeader(value = "User-Agent", required = false) userAgent: String?,
    ): TokenResponse = authenticationService.refresh(request.refreshToken, userAgent)

    @PostMapping("/token/biometric")
    fun biometric(
        @Valid @RequestBody request: BiometricGrantRequest,
        @RequestHeader(value = "User-Agent", required = false) userAgent: String?,
    ): TokenResponse = authenticationService.signInWithBiometric(request, userAgent)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @RequestBody(required = false) request: RefreshTokenRequest?,
        @AuthenticationPrincipal jwt: Jwt?,
    ) {
        val userId = jwt?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        authenticationService.signOut(request?.refreshToken, userId)
    }

    @GetMapping("/devices")
    fun listDevices(
        @AuthenticationPrincipal jwt: Jwt,
    ): List<DeviceResponse> = deviceService.listForUser(currentUserId(jwt), currentDeviceId(jwt))

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerDevice(
        @Valid @RequestBody request: RegisterDeviceRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): DeviceResponse = deviceService.register(currentUserId(jwt), request)

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeDevice(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) = deviceService.revoke(currentUserId(jwt), id)

    /**
     * Public JWK set.
     *
     * Published so that future services, the API gateway and third-party integrations can verify
     * our tokens without holding any credential of their own. The application itself does not use
     * this endpoint — it decodes with the in-process public key rather than calling itself over
     * HTTP.
     */
    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> = mapOf("keys" to listOf(rsaKey.toPublicJWK().toJSONObject()))

    private fun currentUserId(jwt: Jwt): UUID =
        runCatching { UUID.fromString(jwt.subject) }.getOrElse {
            throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "Token subject is not a valid user id")
        }

    private fun currentDeviceId(jwt: Jwt): UUID? =
        jwt.getClaimAsString(TokenService.CLAIM_DEVICE_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

/**
 * Tenant resolution — the first screen of sign-in.
 *
 * Lives in the identity module because it is part of the authentication journey, and returns only
 * organisation-level facts. It deliberately reveals nothing about whether a *user* exists, so it
 * cannot be used to enumerate accounts.
 */
@RestController
@RequestMapping("/v1/auth")
class TenantResolveController {
    @PostMapping("/resolve-tenant")
    @Transactional(readOnly = true)
    fun resolve(
        @RequestBody request: ResolveTenantRequest,
    ): ResolveTenantResponse {
        // TenantFilter has already resolved the tenant from the X-Tenant-Code header or the
        // subdomain. If it could not, the organisation does not exist.
        val tenant =
            TenantContext.currentOrNull()
                ?: throw NotFoundException(ErrorCode.TENANT_NOT_FOUND, "Unknown organisation")

        return ResolveTenantResponse(
            code = tenant.code,
            name = tenant.name,
            locale = tenant.locale,
            defaultCurrency = tenant.defaultCurrency,
            timezone = tenant.timezone,
            // SSO discovery lands in Phase 1 (P0-BE-30/31/32). Until then every tenant is
            // password-based, and the client renders the password form.
            authMethods = listOf("PASSWORD"),
        )
    }
}

/**
 * The authenticated user's own context.
 *
 * Called on every cold start, so it returns identity, permissions and enabled modules together.
 * The client must be able to render its navigation shell from one response — making it assemble
 * that from three calls is how a "fast" app becomes a slow one.
 */
@RestController
@RequestMapping("/v1/me")
class MeController(
    private val users: AppUserRepository,
    private val permissionResolver: PermissionResolver,
) {
    @GetMapping
    @Transactional(readOnly = true)
    fun me(
        @AuthenticationPrincipal jwt: Jwt,
    ): MeResponse {
        val userId =
            runCatching { UUID.fromString(jwt.subject) }.getOrElse {
                throw UnauthenticatedException(ErrorCode.TOKEN_INVALID, "Token subject is not a valid user id")
            }
        val user =
            users.findById(userId).orElseThrow {
                UnauthenticatedException(ErrorCode.TOKEN_INVALID, "User no longer exists")
            }
        val tenant = TenantContext.require()

        return MeResponse(
            userId = user.id,
            employeeId = user.employeeId,
            username = user.username,
            email = user.email,
            locale = user.locale ?: tenant.locale,
            timezone = user.timezone ?: tenant.timezone,
            tenant =
                TenantSummaryDto(
                    id = tenant.id,
                    code = tenant.code,
                    name = tenant.name,
                    defaultCurrency = tenant.defaultCurrency,
                    timezone = tenant.timezone,
                    locale = tenant.locale,
                ),
            roles = permissionResolver.rolesFor(user.id),
            permissions = permissionResolver.permissionsFor(user.id).sorted(),
            // Module enablement arrives with the tenant module registry in Phase 1. Until then the
            // client sees the Phase 0 surface only.
            enabledModules = listOf("identity"),
            mustChangePassword = user.mustChangePassword,
            mfaEnabled = user.mfaEnabled,
        )
    }
}
