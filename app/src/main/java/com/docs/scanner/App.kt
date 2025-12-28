package com.docs.scanner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.docs.scanner.util.LogcatCollector
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {

    private var logcatCollector: LogcatCollector? = null

    override fun onCreate() {
        super.onCreate()

        // ✅ Debug tools только в DEBUG режиме
        if (BuildConfig.DEBUG) {
            initializeDebugTools()
        }

        // ✅ Notification channels (всегда, для production)
        createNotificationChannels()
    }

    private fun initializeDebugTools() {
        logcatCollector = LogcatCollector.getInstance(this).apply {
            startCollecting()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            logcatCollector?.forceSave()
        })

        println("🔧 Debug tools initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "term_reminders",
                "Term Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for term deadlines"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            
            println("✅ Notification channel created")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        if (BuildConfig.DEBUG) {
            logcatCollector?.stopCollecting()
            println("🔧 Debug tools terminated")
        }
    }
}