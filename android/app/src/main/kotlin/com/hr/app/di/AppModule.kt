package com.hr.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.hr.app.BuildConfig
import com.hr.app.data.local.DatabaseKeyProvider
import com.hr.app.data.local.HrDatabase
import com.hr.app.data.local.OutboxDao
import com.hr.app.data.local.SyncCursorDao
import com.hr.app.data.sync.ChangeApplier
import com.hr.app.data.sync.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true // An older client must not break when the server adds a field.
            explicitNulls = false
            encodeDefaults = true
        }

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): HrDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, HrDatabase::class.java, HrDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(keyProvider.passphrase()))
            // Everything stored locally is a cache of server state, so a failed migration can be
            // resolved by rebuilding from scratch rather than by blocking the user. The outbox is
            // the one exception, and losing it is still better than an app that will not start —
            // the user is warned and can resubmit.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideOutboxDao(database: HrDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun provideSyncCursorDao(database: HrDatabase): SyncCursorDao = database.syncCursorDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BODY level logs request and response payloads, which include salaries,
                    // national identifiers and bank details. Debug builds only, never release.
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                    )
                }
                if (BuildConfig.CERTIFICATE_PINNING) {
                    // Pins are configured per environment and always include a backup pin, so a
                    // certificate rotation does not brick every installed app.
                    // TODO(P0-AND-08): supply pins from the release configuration.
                }
            }
            .build()

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
}

/**
 * Declares the change-applier map so Hilt can inject an empty one.
 *
 * Without `@Multibinds` the map has no binding at all until the first applier is contributed, and
 * `SyncEngine` fails to construct. Appliers arrive with the entities they apply, in Phase 1.
 */
@Module
@InstallIn(SingletonComponent::class)
interface SyncBindingsModule {
    @Multibinds
    fun changeAppliers(): Map<String, ChangeApplier>
}
