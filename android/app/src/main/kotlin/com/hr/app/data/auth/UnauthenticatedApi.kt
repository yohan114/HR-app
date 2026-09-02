package com.hr.app.data.auth

import javax.inject.Qualifier

/**
 * Marks the HTTP client and API that must not carry a session.
 *
 * A qualifier rather than a naming convention, so that injecting the wrong one is a compile error
 * instead of a runtime recursion. See `AuthModule` for why the distinction matters.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UnauthenticatedApi
