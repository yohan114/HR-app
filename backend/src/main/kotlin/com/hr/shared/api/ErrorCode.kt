package com.hr.shared.api

/**
 * Platform-level error codes.
 *
 * Modules define their own codes as constants in their own package — this object holds only the
 * cross-cutting ones. Codes are part of the public API contract: once published they may be
 * deprecated but never renamed or repurposed.
 */
object ErrorCode {
    // 400
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val MALFORMED_REQUEST = "MALFORMED_REQUEST"
    const val INVALID_CURSOR = "INVALID_CURSOR"
    const val MISSING_IDEMPOTENCY_KEY = "MISSING_IDEMPOTENCY_KEY"

    // 401
    const val UNAUTHENTICATED = "UNAUTHENTICATED"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    const val TOKEN_INVALID = "TOKEN_INVALID"
    const val TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED"
    const val MFA_REQUIRED = "MFA_REQUIRED"

    /**
     * Covers a wrong TOTP code, a wrong recovery code, and a challenge presented for an account
     * with no second factor. Deliberately one code: distinguishing them would tell the holder of a
     * challenge token which of those they are looking at.
     */
    const val MFA_INVALID_CODE = "MFA_INVALID_CODE"
    const val STEP_UP_REQUIRED = "STEP_UP_REQUIRED"

    // 403
    const val FORBIDDEN = "FORBIDDEN"
    const val INSUFFICIENT_PERMISSION = "INSUFFICIENT_PERMISSION"
    const val OUT_OF_DATA_SCOPE = "OUT_OF_DATA_SCOPE"
    const val ACCOUNT_LOCKED = "ACCOUNT_LOCKED"
    const val ACCOUNT_DISABLED = "ACCOUNT_DISABLED"
    const val DEVICE_REVOKED = "DEVICE_REVOKED"

    // 404
    const val NOT_FOUND = "NOT_FOUND"
    const val TENANT_NOT_FOUND = "TENANT_NOT_FOUND"

    // 409
    const val CONFLICT = "CONFLICT"
    const val STALE_VERSION = "STALE_VERSION"
    const val ALREADY_DECIDED = "ALREADY_DECIDED"
    const val DUPLICATE = "DUPLICATE"

    // 422
    const val BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION"

    // 429
    const val RATE_LIMITED = "RATE_LIMITED"

    // 500 / 503
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"

    // Tenancy — these indicate a bug or an attack, never normal operation
    const val TENANT_CONTEXT_MISSING = "TENANT_CONTEXT_MISSING"
    const val TENANT_MISMATCH = "TENANT_MISMATCH"
}
