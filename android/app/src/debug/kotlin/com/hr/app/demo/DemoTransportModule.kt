package com.hr.app.demo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor

/**
 * Contributes the demo transport to both HTTP clients.
 *
 * ## This module is the whole wiring, and it only exists in the debug variant
 *
 * `AppModule` declares `Set<Interceptor>` as a Dagger multibinding with no elements. In the release
 * variant nothing contributes to it, so both clients are built exactly as they were and the empty
 * loop over the set compiles away. In the debug variant this file — which the release build never
 * compiles — puts the fake transport in it.
 *
 * The consequence worth stating plainly: **there is no configuration under which a release build
 * serves fixtures.** Not a flag left on, not a `BuildConfig` field, not an environment variable.
 * The class is absent from the artefact.
 *
 * ## Why the interceptor goes into both clients
 *
 * `AuthModule` deliberately builds a second `OkHttpClient` for the endpoints that establish a
 * session, so a refresh cannot recurse through the interceptor that asks for a token. That
 * separation is a correctness requirement rather than tidiness — and it means a fake transport
 * fitted to only one of the two would leave sign-in reaching for a server that is not there.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DemoTransportModule {
    @Binds
    @IntoSet
    fun bindDemoTransport(interceptor: DemoTransportInterceptor): Interceptor
}
