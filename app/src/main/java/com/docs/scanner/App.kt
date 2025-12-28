package com.docs.scanner

import android.app.Application
import com.docs.scanner.BuildConfig
import com.docs.scanner.util.LogcatCollector
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {

    private var logcatCollector: LogcatCollector? = null

    override fun onCreate() {
        super.onCreate()

        // ✅ КРИТИЧНО: LogcatCollector только в DEBUG!
        if (BuildConfig.DEBUG) {
            initializeDebugTools()
        }
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

    override fun onTerminate() {
        super.onTerminate()
        if (BuildConfig.DEBUG) {
            logcatCollector?.stopCollecting()
        }
    }