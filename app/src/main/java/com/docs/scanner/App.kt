package com.docs.scanner

import android.app.Application
import com.docs.scanner.util.LogcatCollector
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {

    private lateinit var logcatCollector: LogcatCollector

    override fun onCreate() {
        super.onCreate()

        logcatCollector = LogcatCollector.getInstance(this)
        logcatCollector.startCollecting()

        Runtime.getRuntime().addShutdownHook(Thread {
            logcatCollector.forceSave()
        })
    }

    override fun onTerminate() {
        super.onTerminate()
        logcatCollector.stopCollecting()
    }
}