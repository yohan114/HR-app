package com.hr.tenancy.internal

import com.hr.shared.api.ApiError
import com.hr.shared.api.ApiErrorResponse
import com.hr.shared.api.ApiException
import com.hr.shared.api.GlobalExceptionHandler
import com.hr.tenancy.TenantContext
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Establishes the tenant binding and the request id for the duration of a request, and — most
 * importantly — tears them down afterwards.
 *
 * The `finally` block is not optional. Servlet containers pool threads; a leaked ThreadLocal
 * would bind the next request on that thread to the previous request's tenant.
 *
 * Ordering: this runs *after* Spring Security so that the JWT has already been parsed and the
 * `tenant_id` claim is available. Endpoints that legitimately run before authentication (login,
 * org resolve) supply the tenant via the `X-Tenant-Code` header instead.
 */
@Component
@Order(TenantFilter.ORDER)
class TenantFilter(
    private val tenantResolver: TenantResolver,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER_REQUEST_ID)?.take(64) ?: UUID.randomUUID().toString()
        request.setAttribute(GlobalExceptionHandler.REQUEST_ID_ATTRIBUTE, requestId)
        response.setHeader(HEADER_REQUEST_ID, requestId)
        MDC.put(MDC_REQUEST_ID, requestId)

        try {
            val handle = tenantResolver.resolve(request, currentTokenTenantId())
            if (handle != null) {
                TenantContext.set(handle)
                MDC.put(MDC_TENANT, handle.code)
            }
            filterChain.doFilter(request, response)
        } catch (ex: ApiException) {
            // The exception handler is not in play this early in the chain, so serialise the
            // envelope ourselves rather than letting the container emit an HTML error page.
            writeError(response, ex, requestId)
        } finally {
            TenantContext.clear()
            MDC.remove(MDC_TENANT)
            MDC.remove(MDC_REQUEST_ID)
        }
    }

    /**
     * Reads the `tenant_id` claim from the authenticated principal, if there is one.
     *
     * Returns null for anonymous requests — which is correct for login and org-resolve, and
     * harmless elsewhere because those endpoints require authentication anyway.
     */
    private fun currentTokenTenantId(): UUID? {
        val authentication =
            org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .authentication ?: return null
        val principal = authentication.principal
        if (principal is org.springframework.security.oauth2.jwt.Jwt) {
            return principal.getClaimAsString("tenant_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        }
        return null
    }

    private fun writeError(
        response: HttpServletResponse,
        ex: ApiException,
        requestId: String,
    ) {
        response.status = ex.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(
            response.outputStream,
            ApiErrorResponse(
                ApiError(
                    code = ex.code,
                    message = ex.message ?: ex.code,
                    details = ex.details,
                    requestId = requestId,
                ),
            ),
        )
    }

    /** Never bind a tenant on infrastructure endpoints — they are deliberately tenant-agnostic. */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator") ||
            request.requestURI == "/health" ||
            request.requestURI == "/ready"

    companion object {
        /** After Spring Security (which sits at 0 by default) so the JWT is already parsed. */
        const val ORDER = 100
        const val HEADER_REQUEST_ID = "X-Request-Id"
        const val MDC_REQUEST_ID = "requestId"
        const val MDC_TENANT = "tenant"
    }
}
