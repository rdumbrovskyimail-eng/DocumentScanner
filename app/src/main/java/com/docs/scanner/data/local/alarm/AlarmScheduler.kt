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
        val termTime = term.dueDate  // ✅ ИЗМЕНЕНО: dateTime → dueDate
        val timeUntilTerm = termTime - now
        
        if (timeUntilTerm <= 0) {
            return
        }
        
        val reminders = mutableListOf<ReminderTime>()
        
        if (timeUntilTerm > DAYS_4) {
            reminders.add(ReminderTime(termTime - DAYS_4, "Reminder: ${term.title} in 4 days", 1))
        }
        
        if (timeUntilTerm > DAYS_3) {
            reminders.add(ReminderTime(termTime - DAYS_3, "Reminder: ${term.title} in 3 days", 2))
        }
        
        if (timeUntilTerm > DAYS_2) {
            reminders.add(ReminderTime(termTime - DAYS_2, "Reminder: ${term.title} in 2 days", 3))
        }
        
        if (timeUntilTerm > DAY_1) {
            reminders.add(ReminderTime(termTime - DAY_1, "Reminder: ${term.title} tomorrow", 4))
            reminders.add(ReminderTime(termTime - (DAY_1 - HOURS_12), "Tomorrow: ${term.title}", 5))
        }
        
        if (timeUntilTerm > HOURS_12) {
            reminders.add(ReminderTime(termTime - HOURS_12, "12 hours until: ${term.title}", 6))
        }
        
        if (timeUntilTerm > HOURS_6) {
            reminders.add(ReminderTime(termTime - HOURS_6, "6 hours until: ${term.title}", 7))
        }
        
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
        
        if (timeUntilTerm > HOUR_1) {
            reminders.add(ReminderTime(termTime - HOUR_1, "1 hour until: ${term.title}", 20))
            reminders.add(ReminderTime(termTime - MIN_45, "45 minutes until: ${term.title}", 21))
            reminders.add(ReminderTime(termTime - MIN_30, "30 minutes until: ${term.title}", 22))
            reminders.add(ReminderTime(termTime - MIN_15, "15 minutes until: ${term.title}", 23))
        }
        
        if (timeUntilTerm > MIN_10) {
            reminders.add(ReminderTime(termTime - MIN_10, "10 minutes until: ${term.title}", 24))
        }
        
        if (timeUntilTerm > MIN_5) {
            reminders.add(ReminderTime(termTime - MIN_5, "5 minutes until: ${term.title}", 25))
        }
        
        reminders.add(ReminderTime(termTime, "NOW: ${term.title}", 100))
        
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
