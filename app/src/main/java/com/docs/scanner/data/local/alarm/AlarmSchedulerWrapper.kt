package com.docs.scanner.data.local.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ Обёртка для вашего существующего AlarmScheduler
 * Добавляет методы schedule() и cancel() которые ожидает TermsViewModel
 */
@Singleton
class AlarmSchedulerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * Простой метод schedule для TermsViewModel
     */
    fun schedule(termId: Int, title: String, triggerTime: Long) {
        // Не планируем прошедшие напоминания
        if (triggerTime <= System.currentTimeMillis()) {
            return
        }
        
        val intent = Intent(context, TermAlarmReceiver::class.java).apply {
            putExtra("term_id", termId.toLong())
            putExtra("title", title)
            putExtra("description", null as String?)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            termId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback к неточному будильнику
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Простой метод cancel для TermsViewModel
     */
    fun cancel(termId: Int) {
        val intent = Intent(context, TermAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            termId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
