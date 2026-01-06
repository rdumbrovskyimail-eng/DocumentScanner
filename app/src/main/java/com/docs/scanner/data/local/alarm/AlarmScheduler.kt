/**
 * AlarmScheduler.kt
 * Version: 7.0.1 - FIXED (2026 Standards)
 *
 * ✅ FIX CRITICAL-2: Исправлен импорт TermEntity
 *    БЫЛО:  com.docs.scanner.data.local.database.entities.TermEntity
 *    СТАЛО: com.docs.scanner.data.local.database.entity.TermEntity
 *
 * ✅ FIX MEDIUM: Заменены println() на Timber
 * ✅ FIX MEDIUM: Добавлена синхронизация для thread safety
 * ✅ FIX MINOR: Локализуемые строки вынесены в companion object
 */

package com.docs.scanner.data.local.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.docs.scanner.data.local.database.entity.TermEntity  // ✅ FIX: entity, не entities
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    // ✅ NEW: Lock для thread safety при планировании
    private val scheduleLock = Any()
    
    /**
     * Проверяет возможность установки точных будильников.
     * На Android 12+ требуется разрешение SCHEDULE_EXACT_ALARM.
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // До Android 12 разрешение не требуется
        }
    }
    
    /**
     * Планирует прогрессивные напоминания для термина.
     * 
     * Система напоминаний:
     * - >2 дней: 1 раз в день
     * - 1-2 дня: утро и вечер  
     * - 5ч-1д: каждые 2-3 часа
     * - 1-5ч: каждые 30 минут
     * - <1ч: каждые 15 минут
     * - После дедлайна: каждые 5 минут в течение часа
     */
    fun scheduleTerm(term: TermEntity) {
        // ✅ FIX: Синхронизация для избежания race conditions
        synchronized(scheduleLock) {
            scheduleTermInternal(term)
        }
    }
    
    private fun scheduleTermInternal(term: TermEntity) {
        // Проверка разрешения
        if (!canScheduleExactAlarms()) {
            Timber.w("Cannot schedule exact alarms - permission denied for term ${term.id}")
            // TODO: Показать пользователю диалог с просьбой включить в Settings
            return
        }
        
        val now = System.currentTimeMillis()
        val termTime = term.dueDate
        val timeUntilTerm = termTime - now
        
        if (timeUntilTerm <= 0) {
            Timber.d("Term ${term.id} already passed, skipping scheduling")
            return
        }
        
        val reminders = buildReminderList(term, termTime, timeUntilTerm)
        
        // Планируем все напоминания
        var successCount = 0
        reminders.forEach { reminder ->
            if (scheduleAlarm(
                termId = term.id,
                time = reminder.time,
                title = reminder.message,
                description = term.description,
                requestCode = term.id.toInt() * 1000 + reminder.offset,
                isMainAlarm = reminder.offset == MAIN_ALARM_OFFSET
            )) {
                successCount++
            }
        }
        
        Timber.i("Scheduled $successCount/${reminders.size} alarms for term ${term.id}: ${term.title}")
    }
    
    /**
     * Строит список напоминаний в зависимости от времени до дедлайна.
     */
    private fun buildReminderList(
        term: TermEntity,
        termTime: Long,
        timeUntilTerm: Long
    ): List<ReminderTime> {
        val reminders = mutableListOf<ReminderTime>()
        val now = System.currentTimeMillis()
        val earliestReminderTime = if (term.reminderMinutesBefore > 0) {
            termTime - (term.reminderMinutesBefore * 60_000L)
        } else {
            Long.MAX_VALUE // disable pre-deadline reminders
        }
        
        // Прогрессивные напоминания ДО дедлайна
        when {
            // Больше 2 дней → 1 раз в день
            timeUntilTerm > DAYS_2 -> {
                reminders.add(ReminderTime(termTime - DAYS_2, formatMessage(MSG_DAYS_2, term.title), 1))
                reminders.add(ReminderTime(termTime - DAY_1, formatMessage(MSG_DAY_1, term.title), 2))
            }
            
            // Больше 1 дня → утро и вечер
            timeUntilTerm > DAY_1 -> {
                reminders.add(ReminderTime(termTime - DAY_1, formatMessage(MSG_TOMORROW, term.title), 3))
                reminders.add(ReminderTime(termTime - HOURS_12, formatMessage(MSG_HOURS_12, term.title), 4))
            }
            
            // Меньше 1 дня, но больше 5 часов → каждые 2-3 часа
            timeUntilTerm > HOURS_5 -> {
                reminders.add(ReminderTime(termTime - HOURS_5, formatMessage(MSG_HOURS_5, term.title), 5))
                reminders.add(ReminderTime(termTime - HOURS_3, formatMessage(MSG_HOURS_3, term.title), 6))
                reminders.add(ReminderTime(termTime - HOUR_1, formatMessage(MSG_HOUR_1, term.title), 7))
            }
            
            // Меньше 5 часов → каждые 30 минут
            timeUntilTerm > HOUR_1 -> {
                reminders.add(ReminderTime(termTime - MIN_60, formatMessage(MSG_MIN_60, term.title), 8))
                reminders.add(ReminderTime(termTime - MIN_30, formatMessage(MSG_MIN_30, term.title), 9))
            }
            
            // Последний час → каждые 15 минут
            timeUntilTerm > MIN_15 -> {
                reminders.add(ReminderTime(termTime - MIN_30, formatMessage(MSG_MIN_30, term.title), 10))
                reminders.add(ReminderTime(termTime - MIN_15, formatMessage(MSG_MIN_15, term.title), 11))
            }
        }

        // Respect user reminderMinutesBefore: keep only reminders within selected window.
        val filteredPre = reminders
            .filter { it.time >= earliestReminderTime }
            .filter { it.time > now }
            .toMutableList()
        reminders.clear()
        reminders.addAll(filteredPre)
        
        // Главное уведомление (в момент дедлайна)
        reminders.add(ReminderTime(termTime, formatMessage(MSG_NOW, term.title), MAIN_ALARM_OFFSET))
        
        // Повторяющиеся напоминания ПОСЛЕ дедлайна (каждые 5 минут в течение часа)
        // Only if user enabled reminders.
        if (term.reminderMinutesBefore > 0) {
            for (i in 1..POST_DEADLINE_REMINDERS) {
                reminders.add(
                    ReminderTime(
                        termTime + (i * MIN_5),
                        formatMessage(MSG_REMINDER, term.title),
                        MAIN_ALARM_OFFSET + i
                    )
                )
            }
        }
        
        return reminders
    }
    
    /**
     * Отменяет все напоминания для термина.
     */
    fun cancelTerm(termId: Long) {
        synchronized(scheduleLock) {
            // Отменяем все возможные напоминания (до 150 штук)
            for (offset in 0..MAX_REMINDER_OFFSET) {
                cancelAlarm(termId.toInt() * 1000 + offset)
            }
            Timber.d("Cancelled all alarms for term $termId")
        }
    }
    
    /**
     * Планирует одиночный будильник.
     * @return true если успешно запланирован, false иначе
     */
    private fun scheduleAlarm(
        termId: Long,
        time: Long,
        title: String,
        description: String?,
        requestCode: Int,
        isMainAlarm: Boolean = false
    ): Boolean {
        if (time <= System.currentTimeMillis()) {
            return false
        }
        
        val intent = Intent(context, TermAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TERM_ID, termId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESCRIPTION, description)
            putExtra(EXTRA_IS_MAIN_ALARM, isMainAlarm)
            putExtra(EXTRA_NOTIFICATION_OFFSET, requestCode % 1000)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        time,
                        pendingIntent
                    )
                    true
                } else {
                    // Fallback: неточный будильник
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        time,
                        pendingIntent
                    )
                    Timber.w("Scheduled inexact alarm (no SCHEDULE_EXACT_ALARM permission)")
                    false
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time,
                    pendingIntent
                )
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule alarm for term $termId")
            false
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
    
    /**
     * Форматирует сообщение напоминания.
     * TODO: В будущем использовать string resources для локализации
     */
    private fun formatMessage(template: String, title: String): String {
        return template.replace("%s", title)
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
        private const val MIN_15 = 15 * MIN_1
        private const val MIN_30 = 30 * MIN_1
        private const val MIN_60 = 60 * MIN_1
        private const val HOUR_1 = 60 * MIN_1
        private const val HOURS_3 = 3 * HOUR_1
        private const val HOURS_5 = 5 * HOUR_1
        private const val HOURS_12 = 12 * HOUR_1
        private const val DAY_1 = 24 * HOUR_1
        private const val DAYS_2 = 2 * DAY_1
        
        // Настройки напоминаний
        private const val MAIN_ALARM_OFFSET = 100
        private const val POST_DEADLINE_REMINDERS = 12
        private const val MAX_REMINDER_OFFSET = 150
        
        // Intent extras
        const val EXTRA_TERM_ID = "term_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_IS_MAIN_ALARM = "is_main_alarm"
        const val EXTRA_NOTIFICATION_OFFSET = "notification_offset"
        
        // Шаблоны сообщений (TODO: перенести в string resources)
        private const val MSG_DAYS_2 = "2 days until: %s"
        private const val MSG_DAY_1 = "1 day until: %s"
        private const val MSG_TOMORROW = "Tomorrow: %s"
        private const val MSG_HOURS_12 = "12 hours until: %s"
        private const val MSG_HOURS_5 = "5 hours until: %s"
        private const val MSG_HOURS_3 = "3 hours until: %s"
        private const val MSG_HOUR_1 = "1 hour until: %s"
        private const val MSG_MIN_60 = "60 minutes until: %s"
        private const val MSG_MIN_30 = "30 minutes until: %s"
        private const val MSG_MIN_15 = "15 minutes until: %s"
        private const val MSG_NOW = "⏰ NOW: %s"
        private const val MSG_REMINDER = "⏰ REMINDER: %s"
    }
}