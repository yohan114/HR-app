package com.hr.app.di

import com.hr.app.data.sync.OutboxHttpSender
import com.hr.app.data.sync.OutboxSender
import com.hr.app.data.sync.SyncApi
import com.hr.app.data.sync.SyncPage
import com.hr.app.data.sync.SyncScopeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindOutboxSender(impl: OutboxHttpSender): OutboxSender

    companion object {
        /**
         * Which scopes this user syncs.
         *
         * **Empty until Phase 1.** Scopes are derived from the permissions returned by
         * `GET /v1/me`, and there are no syncable business entities yet — the only tables are the
         * outbox and the cursor store.
         *
         * Returning an empty list is what keeps the whole read path dormant: `SyncWorker`
         * iterates nothing, so [SyncApi] is never called. That is deliberate rather than
         * incidental — it means the unimplemented endpoint below cannot be reached by accident.
         */
        @Provides
        @Singleton
        fun provideSyncScopeProvider(): SyncScopeProvider = SyncScopeProvider { emptyList() }

        /**
         * The `GET /v1/sync` client.
         *
         * The server endpoint does not exist yet (P0-BE-33), which is why it is absent from
         * `spec/openapi.yaml` and therefore from the generated clients — a documented-but-missing
         * operation would ship a client method that 404s.
         *
         * This throws rather than returning an empty page. An empty page would look like a
         * successful sync that found no changes, and the first symptom would be a screen that is
         * silently, permanently stale. Failing loudly is the correct behaviour for a seam that is
         * not connected.
         */
        @Provides
        @Singleton
        fun provideSyncApi(): SyncApi =
            object : SyncApi {
                override suspend fun fetch(
                    scope: String,
                    since: String?,
                    limit: Int,
                ): SyncPage =
                    throw NotImplementedError(
                        "GET /v1/sync is not implemented yet (P0-BE-33). No scopes are subscribed, " +
                            "so this should be unreachable — reaching it means SyncScopeProvider " +
                            "returned a scope before the endpoint existed.",
                    )
            }
    }
}
