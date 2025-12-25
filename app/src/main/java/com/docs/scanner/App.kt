package com.docs.scanner

import android.app.Application
import com.docs.scanner.util.LogcatCollector
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    
    private lateinit var logcatCollector: LogcatCollector
    
    override fun onCreate() {
        super.onCreate()
        
        // ✅ ЗАПУСКАЕМ ЛОГИРОВАНИЕ ПЕРВЫМ ДЕЛОМ
        logcatCollector = LogcatCollector.getInstance(this)
        logcatCollector.startCollecting()
        
        android.util.Log.d("App", "=".repeat(60))
        android.util.Log.d("App", "✅ DocumentScanner v2.1.0 started")
        android.util.Log.d("App", "📱 LogcatCollector: ACTIVE")
        android.util.Log.d("App", "=".repeat(60))
        
        // Shutdown hook для сохранения логов при закрытии
        Runtime.getRuntime().addShutdownHook(Thread {
            android.util.Log.d("App", "🛑 App shutting down, saving logs...")
            logcatCollector.forceSave()
        })
    }
    
    override fun onTerminate() {
        super.onTerminate()
        android.util.Log.d("App", "⏹️ App terminated")
        logcatCollector.stopCollecting()
    }
}
