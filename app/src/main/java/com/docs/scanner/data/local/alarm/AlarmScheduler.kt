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
    
    /**
     * Умная система напоминаний:
     * - За 2-4 дня: 1 напоминание в день
     * - За 1 день: 2 напоминания
     * - За 12 часов: 3 напоминания
     * - За 5 часов: каждый час (5-8 напоминаний)
     * - За 1 час: каждые 15 минут
     */
    fun scheduleTerm(term: TermEntity) {
        val now = System.currentTimeMillis()
        val termTime = term.dateTime
        val timeUntilTerm = termTime - now
        
        // Не планируем прошедшие термины
        if (timeUntilTerm <= 0) {
            return
        }
        
        val reminders = mutableListOf<ReminderTime>()
        
        // ✅ За 4 дня (если термин далеко)
        if (timeUntilTerm > DAYS_4) {
            reminders.add(ReminderTime(termTime - DAYS_4, "Reminder: ${term.title} in 4 days", 1))
        }
        
        // ✅ За 3 дня
        if (timeUntilTerm > DAYS_3) {
            reminders.add(ReminderTime(termTime - DAYS_3, "Reminder: ${term.title} in 3 days", 2))
        }
        
        // ✅ За 2 дня
        if (timeUntilTerm > DAYS_2) {
            reminders.add(ReminderTime(termTime - DAYS_2, "Reminder: ${term.title} in 2 days", 3))
        }
        
        // ✅ За 1 день (утром и вечером)
        if (timeUntilTerm > DAY_1) {
            reminders.add(ReminderTime(termTime - DAY_1, "Reminder: ${term.title} tomorrow", 4))
            reminders.add(ReminderTime(termTime - (DAY_1 - HOURS_12), "Tomorrow: ${term.title}", 5))
        }
        
        // ✅ За 12 часов
        if (timeUntilTerm > HOURS_12) {
            reminders.add(ReminderTime(termTime - HOURS_12, "12 hours until: ${term.title}", 6))
        }
        
        // ✅ За 6 часов
        if (timeUntilTerm > HOURS_6) {
            reminders.add(ReminderTime(termTime - HOURS_6, "6 hours until: ${term.title}", 7))
        }
        
        // ✅ За 5-2 часа: каждый час
        for (hour in 5 downTo 2) {
            val hourTime = hour * HOUR_1
            if (timeUntilTerm > hourTime) {
                reminders.add(ReminderTime(
                    termTime - hourTime, 
                    "$hour hours until: ${term.title}", 
                    7 + hour
                ))
            }
        }
        
        // ✅ За 1 час: каждые 15 минут (4 напоминания)
        if (timeUntilTerm > HOUR_1) {
            reminders.add(ReminderTime(termTime - HOUR_1, "1 hour until: ${term.title}", 20))
            reminders.add(ReminderTime(termTime - MIN_45, "45 minutes until: ${term.title}", 21))
            reminders.add(ReminderTime(termTime - MIN_30, "30 minutes until: ${term.title}", 22))
            reminders.add(ReminderTime(termTime - MIN_15, "15 minutes until: ${term.title}", 23))
        }
        
        // ✅ За 10 минут
        if (timeUntilTerm > MIN_10) {
            reminders.add(ReminderTime(termTime - MIN_10, "10 minutes until: ${term.title}", 24))
        }
        
        // ✅ За 5 минут
        if (timeUntilTerm > MIN_5) {
            reminders.add(ReminderTime(termTime - MIN_5, "5 minutes until: ${term.title}", 25))
        }
        
        // ✅ Основное напоминание в момент термина
        reminders.add(ReminderTime(termTime, "NOW: ${term.title}", 100))
        
        // Планируем все напоминания
        reminders.forEach { reminder ->
            scheduleAlarm(
                termId = term.id,
                time = reminder.time,
                title = reminder.message,
                description = term.description,
                requestCode = term.id.toInt() * 1000 + reminder.offset
            )
        }
    }
    
    fun cancelTerm(termId: Long) {
        // Отменяем все возможные напоминания (offset 0-100)
        for (offset in 0..100) {
            cancelAlarm(termId.toInt() * 1000 + offset)
        }
    }
    
    private fun scheduleAlarm(
        termId: Long,
        time: Long,
        title: String,
        description: String?,
        requestCode: Int
    ) {
        // Не планируем прошедшие напоминания
        if (time <= System.currentTimeMillis()) {
            return
        }
        
        val intent = Intent(context, TermAlarmReceiver::class.java).apply {
            putExtra("term_id", termId)
            putExtra("title", title)
            putExtra("description", description)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Android 12+ требует разрешение SCHEDULE_EXACT_ALARM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                time,
                pendingIntent
            )
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
    }
    
    private data class ReminderTime(
        val time: Long,
        val message: String,
        val offset: Int
    )
    
    companion object {
        // Временные константы
        private const val MIN_1 = 60_000L
        private const val MIN_5 = 5 * MIN_1
        private const val MIN_10 = 10 * MIN_1
        private const val MIN_15 = 15 * MIN_1
        private const val MIN_30 = 30 * MIN_1
        private const val MIN_45 = 45 * MIN_1
        private const val HOUR_1 = 60 * MIN_1
        private const val HOURS_6 = 6 * HOUR_1
        private const val HOURS_12 = 12 * HOUR_1
        private const val DAY_1 = 24 * HOUR_1
        private const val DAYS_2 = 2 * DAY_1
        private const val DAYS_3 = 3 * DAY_1
        private const val DAYS_4 = 4 * DAY_1
    }
}
