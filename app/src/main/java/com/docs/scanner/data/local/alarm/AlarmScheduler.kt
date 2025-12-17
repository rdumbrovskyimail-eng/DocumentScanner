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
        // Основное напоминание
        scheduleAlarm(
            termId = term.id,
            time = term.dateTime,
            title = term.title,
            description = term.description,
            requestCode = term.id.toInt()
        )
        
        // Дополнительное напоминание за N минут
        if (term.reminderMinutesBefore != null && term.reminderMinutesBefore > 0) {
            val reminderTime = term.dateTime - term.reminderMinutesBefore * 60_000L
            
            scheduleAlarm(
                termId = term.id,
                time = reminderTime,
                title = "Reminder: ${term.title}",
                description = "In ${term.reminderMinutesBefore} minutes",
                requestCode = term.id.toInt() + 1000000 // Offset для reminder
            )
        }
    }
    
    fun cancelTerm(termId: Long) {
        cancelAlarm(termId.toInt())
        cancelAlarm(termId.toInt() + 1000000) // Cancel reminder тоже
    }
    
    private fun scheduleAlarm(
        termId: Long,
        time: Long,
        title: String,
        description: String?,
        requestCode: Int
    ) {
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
        
        // Проверяем, что время в будущем
        if (time <= System.currentTimeMillis()) {
            return
        }
        
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
}
