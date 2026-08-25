package com.hr.identity.internal

import com.hr.tenancy.TenantContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Mints access tokens and generates refresh token secrets.
 *
 * ## What goes in the access token
 *
 * Identity and *roles*, not permissions. The distinction matters: role sets are small and stable,
 * whereas a user's effective permission list can run to dozens of entries and would bloat every
 * request header. More importantly, permissions are resolved per request from the role set (see
 * [PermissionResolver]), so a permission change takes effect on the very next call rather than
 * whenever the token happens to expire.
 *
 * ## Why 15 minutes
 *
 * Short enough that a leaked access token has limited value and that account status changes
 * propagate quickly; long enough that a normal session does not spend its time refreshing. The
 * refresh token carries the long-lived authority and is revocable, which is where real
 * revocation happens.
 */
@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val rsaKey: com.nimbusds.jose.jwk.RSAKey,
    @Value("\${hr.auth.access-token-ttl:PT15M}") private val accessTokenTtl: Duration,
    @Value("\${hr.auth.refresh-token-ttl:P30D}") private val refreshTokenTtl: Duration,
    @Value("\${hr.auth.issuer:https://api.hrapp.io}") private val issuer: String,
) {
    private val secureRandom = SecureRandom()

    fun issueAccessToken(
        user: AppUser,
        roles: List<String>,
        deviceId: UUID?,
        authMethod: LoginMethod,
    ): IssuedAccessToken {
        val now = Instant.now()
        val expiresAt = now.plus(accessTokenTtl)

        val claims =
            JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.id.toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TENANT_ID, TenantContext.currentId().toString())
                .claim(CLAIM_ROLES, roles)
                .apply {
                    user.employeeId?.let { claim(CLAIM_EMPLOYEE_ID, it.toString()) }
                    deviceId?.let { claim(CLAIM_DEVICE_ID, it.toString()) }
                    // Lets step-up-protected endpoints (payslips, bank details) tell whether the
                    // session was established with a real user presence check.
                    claim(CLAIM_AUTH_METHOD, authMethod.name)
                }
                .build()

        val header = JwsHeader.with { "RS256" }.keyId(rsaKey.keyID).build()
        val jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))

        return IssuedAccessToken(
            value = jwt.tokenValue,
            expiresAt = expiresAt,
            expiresInSeconds = accessTokenTtl.seconds,
        )
    }

    /**
     * Generates a refresh token secret and its storage hash.
     *
     * 256 bits from a CSPRNG. The plaintext is returned to the caller once and never persisted;
     * only the SHA-256 hash is stored, so a database compromise does not yield usable tokens.
     */
    fun generateRefreshToken(): GeneratedRefreshToken {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES).also(secureRandom::nextBytes)
        val value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return GeneratedRefreshToken(
            value = value,
            hash = hash(value),
            expiresAt = Instant.now().plus(refreshTokenTtl),
            expiresInSeconds = refreshTokenTtl.seconds,
        )
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val CLAIM_TENANT_ID = "tenant_id"
        const val CLAIM_EMPLOYEE_ID = "employee_id"
        const val CLAIM_DEVICE_ID = "device_id"
        const val CLAIM_ROLES = "roles"
        const val CLAIM_AUTH_METHOD = "auth_method"

        private const val REFRESH_TOKEN_BYTES = 32
    }
}

data class IssuedAccessToken(
    val value: String,
    val expiresAt: Instant,
    val expiresInSeconds: Long,
)

data class GeneratedRefreshToken(
    val value: String,
    val hash: String,
    val expiresAt: Instant,
    val expiresInSeconds: Long,
)
