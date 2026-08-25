package com.hr.identity.internal

import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import org.slf4j.LoggerFactory
import org.springframework.core.convert.converter.Converter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

/**
 * Turns a validated JWT into an authenticated principal carrying the caller's effective
 * permissions as granted authorities.
 *
 * This is where the role-to-permission expansion happens, which is why permissions are not baked
 * into the token: a permission granted or withdrawn takes effect on the next request rather than
 * at token expiry.
 *
 * Exposed as a `Converter<Jwt, AbstractAuthenticationToken>` — a Spring framework type — so the
 * central security configuration can consume it without reaching into this module's internals.
 * That keeps `ModuleStructureTest` green while leaving authentication owned by the identity module.
 */
@Configuration
class JwtAuthenticationConverterConfig {
    @Bean
    fun jwtAuthenticationConverter(permissionResolver: PermissionResolver): Converter<Jwt, AbstractAuthenticationToken> =
        PermissionExpandingJwtConverter(permissionResolver)
}

private class PermissionExpandingJwtConverter(
    private val permissionResolver: PermissionResolver,
) : Converter<Jwt, AbstractAuthenticationToken> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId = runCatching { UUID.fromString(jwt.subject) }.getOrNull()
        val tenantId =
            jwt.getClaimAsString(TokenService.CLAIM_TENANT_ID)
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val authorities: Collection<GrantedAuthority> =
            if (userId == null || tenantId == null) {
                // A structurally valid token that we nonetheless cannot attribute. Authenticate
                // with no authorities rather than throwing — the request will fail authorisation
                // on its own, and this keeps token-shape problems out of the auth error path.
                log.warn("Token missing subject or tenant claim (jti={})", jwt.id)
                emptyList()
            } else {
                // The tenant binding is established by TenantFilter, which runs *after* this
                // converter. PermissionResolver needs it now, so bind it for the lookup.
                resolveAuthorities(tenantId, userId)
            }

        // Roles from the token are exposed as ROLE_ authorities alongside permissions, so both
        // hasRole() and hasAuthority() work in @PreAuthorize expressions.
        val roleAuthorities =
            jwt.getClaimAsStringList(TokenService.CLAIM_ROLES)
                ?.map { SimpleGrantedAuthority("ROLE_${it.uppercase()}") }
                .orEmpty()

        return JwtAuthenticationToken(jwt, authorities + roleAuthorities, jwt.subject)
    }

    private fun resolveAuthorities(
        tenantId: UUID,
        userId: UUID,
    ): Collection<GrantedAuthority> {
        val existing = TenantContext.currentOrNull()
        return try {
            if (existing == null) {
                TenantContext.runAs(minimalHandle(tenantId)) {
                    permissionResolver.permissionsFor(userId).map(::SimpleGrantedAuthority)
                }
            } else {
                permissionResolver.permissionsFor(userId).map(::SimpleGrantedAuthority)
            }
        } catch (e: Exception) {
            // Failing closed: an authenticated caller with no authorities can reach nothing that
            // requires a permission. Better than a 500 on every request during a database blip.
            log.error("Failed to resolve permissions for user {} in tenant {}", userId, tenantId, e)
            emptyList()
        }
    }

    /**
     * A handle sufficient to bind the connection for the permission lookup.
     *
     * The full tenant record is loaded moments later by `TenantFilter`; only the id is needed to
     * satisfy row-level security here, and loading the registry entry twice per request would be
     * wasteful.
     */
    private fun minimalHandle(tenantId: UUID) =
        TenantHandle(
            id = tenantId,
            code = "",
            name = "",
            dataRegion = "default",
            defaultCurrency = "",
            timezone = "UTC",
            locale = "en",
            isolationTier = com.hr.tenancy.IsolationTier.SHARED,
            status = com.hr.tenancy.TenantStatus.ACTIVE,
        )
}
