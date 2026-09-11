package com.hr.notification.internal.push

/**
 * Result of a push notification dispatch attempt.
 */
sealed interface PushResult {
    data class Success(val messageId: String) : PushResult
    data class Failure(val errorMessage: String, val isInvalidToken: Boolean = false) : PushResult
}

/**
 * Platform push provider gateway interface.
 */
interface PushGateway {
    fun send(
        token: String,
        title: String,
        body: String,
        deepLink: String? = null,
        data: Map<String, String> = emptyMap(),
    ): PushResult
}
