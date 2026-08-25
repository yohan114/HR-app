package com.hr.shared.api

import com.fasterxml.jackson.core.JsonProcessingException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException

/**
 * Translates every exception into the single [ApiErrorResponse] envelope.
 *
 * Rule: clients get a stable `code` plus structured `details`. They never get a stack trace, a
 * SQL message, or an internal class name.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(
        ex: ApiException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        // 4xx are expected traffic — log at DEBUG. 5xx are bugs — log loudly.
        if (ex.status.is5xxServerError) {
            log.error("API error {} on {} {}", ex.code, request.method, request.requestURI, ex)
        } else {
            log.debug("API error {} on {} {}: {}", ex.code, request.method, request.requestURI, ex.message)
        }
        val headers = HttpHeaders()
        if (ex is RateLimitedException) {
            headers.add("Retry-After", ex.retryAfterSeconds.toString())
        }
        return ResponseEntity
            .status(ex.status)
            .headers(headers)
            .body(
                ApiErrorResponse(
                    ApiError(
                        code = ex.code,
                        message = ex.message ?: ex.code,
                        field = ex.field,
                        details = ex.details,
                        requestId = requestId(request),
                    ),
                ),
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val violations =
            ex.bindingResult.fieldErrors.map {
                FieldViolation(
                    field = it.field,
                    code = it.code ?: "INVALID",
                    message = it.defaultMessage ?: "Invalid value",
                    rejectedValue = it.rejectedValue,
                )
            }
        return badRequest(ErrorCode.VALIDATION_FAILED, "Request validation failed", violations, request)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val violations =
            ex.constraintViolations.map {
                FieldViolation(
                    field = it.propertyPath.toString(),
                    code = "INVALID",
                    message = it.message,
                    rejectedValue = it.invalidValue,
                )
            }
        return badRequest(ErrorCode.VALIDATION_FAILED, "Request validation failed", violations, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, JsonProcessingException::class)
    fun handleUnreadable(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        badRequest(ErrorCode.MALFORMED_REQUEST, "Request body could not be parsed", null, request)

    @ExceptionHandler(MissingServletRequestParameterException::class, MethodArgumentTypeMismatchException::class)
    fun handleBadParam(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        badRequest(ErrorCode.VALIDATION_FAILED, "Invalid or missing request parameter", null, request)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleStaleVersion(
        ex: OptimisticLockingFailureException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                ApiError(
                    code = ErrorCode.STALE_VERSION,
                    message = "The record was modified by someone else. Reload and retry.",
                    requestId = requestId(request),
                ),
            ),
        )

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(
        ex: AuthenticationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse(
                ApiError(
                    code = ErrorCode.UNAUTHENTICATED,
                    message = "Authentication required",
                    requestId = requestId(request),
                ),
            ),
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiErrorResponse(
                ApiError(
                    code = ErrorCode.INSUFFICIENT_PERMISSION,
                    message = "Not permitted",
                    requestId = requestId(request),
                ),
            ),
        )

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandler(
        ex: NoHandlerFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                ApiError(
                    code = ErrorCode.NOT_FOUND,
                    message = "No such endpoint",
                    requestId = requestId(request),
                ),
            ),
        )

    /**
     * Catch-all. Nothing internal is exposed: the caller gets a bare code and a request id they
     * can quote to support.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val id = requestId(request)
        log.error("Unhandled exception [requestId={}] on {} {}", id, request.method, request.requestURI, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(
                ApiError(
                    code = ErrorCode.INTERNAL_ERROR,
                    message = "An unexpected error occurred",
                    requestId = id,
                ),
            ),
        )
    }

    private fun badRequest(
        code: String,
        message: String,
        violations: List<FieldViolation>?,
        request: HttpServletRequest,
    ) = ResponseEntity.badRequest().body(
        ApiErrorResponse(
            ApiError(
                code = code,
                message = message,
                violations = violations,
                requestId = requestId(request),
            ),
        ),
    )

    private fun requestId(request: HttpServletRequest): String? =
        request.getAttribute(REQUEST_ID_ATTRIBUTE) as? String

    companion object {
        const val REQUEST_ID_ATTRIBUTE = "com.hr.requestId"
    }
}
