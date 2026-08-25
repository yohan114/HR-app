package com.hr.shared.api

import org.springframework.http.HttpStatus

/**
 * Base type for every deliberately-thrown API error.
 *
 * Anything that is not an [ApiException] reaching the exception handler is treated as an
 * unexpected internal error: it is logged at ERROR with a stack trace and returned to the caller
 * as a bare `INTERNAL_ERROR` with no detail. That asymmetry is intentional — we never leak
 * internal messages to clients.
 */
open class ApiException(
    val status: HttpStatus,
    val code: String,
    message: String,
    val field: String? = null,
    val details: Map<String, Any?>? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class BadRequestException(
    code: String = ErrorCode.MALFORMED_REQUEST,
    message: String,
    field: String? = null,
    details: Map<String, Any?>? = null,
) : ApiException(HttpStatus.BAD_REQUEST, code, message, field, details)

class UnauthenticatedException(
    code: String = ErrorCode.UNAUTHENTICATED,
    message: String = "Authentication required",
    details: Map<String, Any?>? = null,
) : ApiException(HttpStatus.UNAUTHORIZED, code, message, null, details)

class ForbiddenException(
    code: String = ErrorCode.FORBIDDEN,
    message: String = "Not permitted",
    details: Map<String, Any?>? = null,
) : ApiException(HttpStatus.FORBIDDEN, code, message, null, details)

class NotFoundException(
    code: String = ErrorCode.NOT_FOUND,
    message: String = "Not found",
    details: Map<String, Any?>? = null,
) : ApiException(HttpStatus.NOT_FOUND, code, message, null, details)

class ConflictException(
    code: String = ErrorCode.CONFLICT,
    message: String,
    details: Map<String, Any?>? = null,
) : ApiException(HttpStatus.CONFLICT, code, message, null, details)

/**
 * A request that is syntactically valid but violates a domain rule.
 *
 * Always carry enough in [details] for the client to explain *why* without a second round trip.
 * "Insufficient balance" is a bad error; "insufficient balance: available 2.5, requested 5" is a
 * good one. See docs/05-screens-ux.md principle 4 — show your working.
 */
class BusinessRuleException(
    code: String,
    message: String,
    field: String? = null,
    details: Map<String, Any?>? = null,
) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, field, details)

class RateLimitedException(
    val retryAfterSeconds: Long,
    message: String = "Too many requests",
) : ApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        ErrorCode.RATE_LIMITED,
        message,
        null,
        mapOf("retryAfterSeconds" to retryAfterSeconds),
    )
