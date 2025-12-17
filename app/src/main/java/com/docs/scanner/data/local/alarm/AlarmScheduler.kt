package com.docs.scanner.data.local.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.docs.scanner.data.local.database.entities.TermEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    fun scheduleTerm(term: TermEntity) {
        val now = System.currentTimeMillis()
        val termTime = term.dueDate
        val timeUntilTerm = termTime - now
        
        if (timeUntilTerm <= 0) {
            return  // Уже прошло
        }
        
        val reminders = mutableListOf<ReminderTime>()
        
        // ✅ ПРОГРЕССИВНЫЕ НАПОМИНАНИЯ ДО дедлайна
        when {
            // Больше 2 дней → 1 раз в день
            timeUntilTerm > DAYS_2 -> {
                reminders.add(ReminderTime(termTime - DAYS_2, "2 days until: ${term.title}", 1))
                reminders.add(ReminderTime(termTime - DAY_1, "1 day until: ${term.title}", 2))
            }
            
            // Больше 1 дня → утро и вечер
            timeUntilTerm > DAY_1 -> {
                reminders.add(ReminderTime(termTime - DAY_1, "Tomorrow: ${term.title}", 3))
                reminders.add(ReminderTime(termTime - HOURS_12, "12 hours until: ${term.title}", 4))
            }
            
            // Меньше 1 дня, но больше 5 часов → каждые 2-3 часа
            timeUntilTerm > HOURS_5 -> {
                reminders.add(ReminderTime(termTime - HOURS_5, "5 hours until: ${term.title}", 5))
                reminders.add(ReminderTime(termTime - HOURS_3, "3 hours until: ${term.title}", 6))
                reminders.add(ReminderTime(termTime - HOUR_1, "1 hour until: ${term.title}", 7))
            }
            
            // Меньше 5 часов → каждые 30 минут
            timeUntilTerm > HOUR_1 -> {
                reminders.add(ReminderTime(termTime - MIN_60, "60 minutes until: ${term.title}", 8))
                reminders.add(ReminderTime(termTime - MIN_30, "30 minutes until: ${term.title}", 9))
            }
            
            // Последний час → каждые 15 минут
            timeUntilTerm > MIN_15 -> {
                reminders.add(ReminderTime(termTime - MIN_30, "30 minutes until: ${term.title}", 10))
                reminders.add(ReminderTime(termTime - MIN_15, "15 minutes until: ${term.title}", 11))
            }
        }
        
        // ✅ ГЛАВНОЕ УВЕДОМЛЕНИЕ (звонок в момент дедлайна)
        reminders.add(ReminderTime(termTime, "⏰ NOW: ${term.title}", 100))
        
        // ✅ ПОВТОРЯЮЩИЕСЯ НАПОМИНАНИЯ ПОСЛЕ дедлайна (каждые 5 минут в течение часа)
        for (i in 1..12) {
            reminders.add(
                ReminderTime(
                    termTime + (i * MIN_5),
                    "⏰ REMINDER: ${term.title}",
                    100 + i
                )
            )
        }
        
        // Планируем все напоминания
        reminders.forEach { reminder ->
            scheduleAlarm(
                termId = term.id,
                time = reminder.time,
                title = reminder.message,
                description = term.description,
                requestCode = term.id.toInt() * 1000 + reminder.offset,
                isMainAlarm = reminder.offset == 100
            )
        }
    }
    
    fun cancelTerm(termId: Long) {
        // Отменяем все возможные напоминания (до 112 штук)
        for (offset in 0..150) {
            cancelAlarm(termId.toInt() * 1000 + offset)
        }
    }
    
    private fun scheduleAlarm(
        termId: Long,
        time: Long,
        title: String,
        description: String?,
        requestCode: Int,
        isMainAlarm: Boolean = false
    ) {
        if (time <= System.currentTimeMillis()) {
            return
        }
        
        val intent = Intent(context, TermAlarmReceiver::class.java).apply {
            putExtra("term_id", termId)
            putExtra("title", title)
            putExtra("description", description)
            putExtra("is_main_alarm", isMainAlarm)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        time,
                        pendingIntent
                    )
                    println("✅ Scheduled exact alarm for $time (main=$isMainAlarm)")
                } else {
                    // Fallback: неточный будильник
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        time,
                        pendingIntent
                    )
                    println("⚠️ Scheduled inexact alarm (no permission)")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time,
                    pendingIntent
                )
                println("✅ Scheduled exact alarm for $time")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to schedule alarm: ${e.message}")
        }
    }
    
    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, TermAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    
    private data class ReminderTime(
        val time: Long,
        val message: String,
        val offset: Int
    )
    
    companion object {
        private const val MIN_1 = 60_000L
        private const val MIN_5 = 5 * MIN_1
        private const val MIN_15 = 15 * MIN_1
        private const val MIN_30 = 30 * MIN_1
        private const val MIN_60 = 60 * MIN_1
        private const val HOUR_1 = 60 * MIN_1
        private const val HOURS_3 = 3 * HOUR_1
        private const val HOURS_5 = 5 * HOUR_1
        private const val HOURS_12 = 12 * HOUR_1
        private const val DAY_1 = 24 * HOUR_1
        private const val DAYS_2 = 2 * DAY_1
    }
}
