package com.hr.shared.api

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * The single error envelope returned by every endpoint in the API.
 *
 * `code` is a stable, machine-readable identifier. Clients localise from `code`, never from
 * `message` — we ship in six languages and the server has no reliable way to know the caller's
 * locale for every code path. `message` is a developer-facing fallback only.
 *
 * See docs/03-architecture.md §9.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorResponse(
    val error: ApiError,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(
    /** Stable machine-readable code, e.g. `LEAVE_BALANCE_INSUFFICIENT`. */
    val code: String,
    /** Developer-facing English description. Never shown to end users verbatim. */
    val message: String,
    /** Field path when the error is attributable to one input field. */
    val field: String? = null,
    /** Structured context the client can use to build a localised message. */
    val details: Map<String, Any?>? = null,
    /** Per-field violations for validation failures. */
    val violations: List<FieldViolation>? = null,
    /** Correlates with server logs and traces. */
    val requestId: String? = null,
)

data class FieldViolation(
    val field: String,
    val code: String,
    val message: String,
    val rejectedValue: Any? = null,
)
