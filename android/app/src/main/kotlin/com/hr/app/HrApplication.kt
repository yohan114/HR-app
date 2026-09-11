package com.hr.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hr.app.notification.HrNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Implements [Configuration.Provider] so WorkManager uses Hilt's worker factory. The sync and
 * outbox workers depend on the database, the API client and the token store; without this they
 * would have to reach for a service locator, and the outbox is not a place to be resolving
 * dependencies by hand.
 */
@HiltAndroidApp
class HrApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationManager: HrNotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager.initNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
                .build()
}
