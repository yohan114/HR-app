package com.hr.identity

import com.hr.identity.internal.TokenService
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.UnauthenticatedException
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

/**
 * Who is making the current request.
 *
 * Published by the identity module because every other module needs it and none
 * of them should be parsing JWT claims. Before this existed the
 * `UUID.fromString(jwt.subject)` dance was copied into each controller, and a
 * copy that forgets the `runCatching` turns a malformed token into a 500 instead
 * of a 401.
 */
data class Caller(
    val userId: UUID,
    /**
     * The caller's own employee record, if they have one.
     *
     * Null for user accounts that are not people in the org chart — platform
     * operators, integration credentials, a support login. Code that treats
     * null as "some employee" would grant those accounts self-service access to
     * whichever record it happened to be looking at.
     */
    val employeeId: UUID?,
    val deviceId: UUID?,
) {
    fun isSelf(employeeId: UUID?): Boolean = this.employeeId != null && this.employeeId == employeeId

    companion object {
        fun from(jwt: Jwt): Caller =
            Caller(
                userId =
                    uuid(jwt.subject)
                        ?: throw UnauthenticatedException(
                            ErrorCode.TOKEN_INVALID,
                            "Token subject is not a valid user id",
                        ),
                employeeId = uuid(jwt.getClaimAsString(TokenService.CLAIM_EMPLOYEE_ID)),
                deviceId = uuid(jwt.getClaimAsString(TokenService.CLAIM_DEVICE_ID)),
            )

        /**
         * A malformed optional claim reads as absent rather than raising.
         *
         * The subject is different — without it there is no caller at all — but
         * a corrupt `employee_id` should cost self-service access, not the whole
         * request. Failing closed here means the caller is treated as having no
         * employee record, which is the restrictive interpretation.
         */
        private fun uuid(value: String?): UUID? = value?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
