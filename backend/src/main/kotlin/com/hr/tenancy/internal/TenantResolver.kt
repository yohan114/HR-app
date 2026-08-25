package com.hr.tenancy.internal

import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import com.hr.shared.api.ApiException
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.ForbiddenException
import com.hr.shared.api.NotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Determines which tenant a request belongs to.
 *
 * Precedence, highest first:
 *
 *  1. **JWT `tenant_id` claim** — authoritative for authenticated requests. The token was issued
 *     by us for a specific tenant, so nothing in the request can override it.
 *  2. **`X-Tenant-Code` header** — used by the unauthenticated org-resolve and login endpoints,
 *     before a token exists.
 *  3. **Subdomain** — `acme.hrapp.io` resolves to tenant `acme`. Convenience for the web console.
 *
 * If a request supplies both a token and a conflicting header, that is a token-confusion attempt
 * and we reject it outright rather than silently preferring one.
 */
@Component
class TenantResolver(
    private val tenantRegistry: TenantRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resolve(
        request: HttpServletRequest,
        tokenTenantId: UUID?,
    ): TenantHandle? {
        val headerCode = request.getHeader(HEADER_TENANT_CODE)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val subdomainCode = subdomainOf(request)

        val handle =
            when {
                tokenTenantId != null -> {
                    val fromToken =
                        tenantRegistry.findById(tokenTenantId)
                            ?: throw NotFoundException(ErrorCode.TENANT_NOT_FOUND, "Tenant no longer exists")
                    // A header naming a different tenant than the token is never legitimate.
                    if (headerCode != null && headerCode != fromToken.code) {
                        log.warn(
                            "Tenant mismatch: token={} header={} uri={}",
                            fromToken.code,
                            headerCode,
                            request.requestURI,
                        )
                        throw ApiException(
                            HttpStatus.FORBIDDEN,
                            ErrorCode.TENANT_MISMATCH,
                            "Token and tenant header disagree",
                        )
                    }
                    fromToken
                }

                headerCode != null ->
                    tenantRegistry.findByCode(headerCode)
                        ?: throw NotFoundException(ErrorCode.TENANT_NOT_FOUND, "Unknown organisation")

                subdomainCode != null -> tenantRegistry.findByCode(subdomainCode)

                else -> null
            } ?: return null

        when (handle.status) {
            TenantStatus.ACTIVE -> Unit
            TenantStatus.PROVISIONING ->
                throw ForbiddenException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "This organisation is still being set up",
                )
            TenantStatus.SUSPENDED ->
                throw ForbiddenException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "This organisation's access has been suspended",
                )
            TenantStatus.ARCHIVED ->
                throw NotFoundException(ErrorCode.TENANT_NOT_FOUND, "Unknown organisation")
        }
        return handle
    }

    private fun subdomainOf(request: HttpServletRequest): String? {
        val host = request.serverName ?: return null
        if (host.equals("localhost", ignoreCase = true) || host.matches(IP_ADDRESS)) return null
        val labels = host.split('.')
        if (labels.size < 3) return null
        val candidate = labels.first().lowercase()
        return candidate.takeUnless { it in RESERVED_SUBDOMAINS }
    }

    companion object {
        const val HEADER_TENANT_CODE = "X-Tenant-Code"
        private val IP_ADDRESS = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val RESERVED_SUBDOMAINS = setOf("www", "api", "app", "admin", "static", "cdn")
    }
}
