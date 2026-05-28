package com.docs.scanner.data.local.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.docs.scanner.presentation.MainActivity
import com.docs.scanner.data.local.database.entity.TermEntity // ✅ Исправлен импорт сущности
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first // ✅ Добавлен импорт для Flow.first()
import timber.log.Timber

class TermAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun appDatabase(): com.docs.scanner.data.local.database.AppDatabase
        fun alarmScheduler(): com.docs.scanner.data.local.alarm.AlarmScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        ReceiverEntryPoint::class.java
                    )
                    val database = entryPoint.appDatabase()
                    val scheduler = entryPoint.alarmScheduler()

                    // ✅ Исправлено: задействуем существующий метод observeActive().first()
                    val activeTerms = database.termDao().observeActive().first()
                    
                    activeTerms.forEach { termEntity ->
                        scheduler.scheduleTerm(termEntity)
                    }
                    Timber.i("✅ Rescheduled ${activeTerms.size} terms after system reboot")
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Timber.e(e, "❌ Failed to reschedule terms on boot")
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val termId = intent.getLongExtra("term_id", -1)
        val title = intent.getStringExtra("title") ?: "Term Reminder"
        val description = intent.getStringExtra("description")
        val isMainAlarm = intent.getBooleanExtra("is_main_alarm", false)
        val offset = intent.getIntExtra("notification_offset", 0)
        
        showNotification(context, termId, offset, title, description, isMainAlarm)
    }

    private fun showNotification(
        context: Context,
        termId: Long,
        offset: Int,
        title: String,
        description: String?,
        isMainAlarm: Boolean
    ) {
        val notificationId = ((termId.hashCode() * 31 + offset) and Int.MAX_VALUE)
        
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_term", termId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Настройка интента для кнопки быстрого сброса будильника
        val dismissIntent = Intent(context, TermAlarmReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(description ?: "You have a term scheduled")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setPriority(if (isMainAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
        
        if (isMainAlarm) {
            builder
                .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setFullScreenIntent(pendingIntent, true)
                .setOngoing(true)
                // FLAG_INSISTENT заставляет звук и вибрацию повторяться непрерывно (режим будильника)
                .setFlag(NotificationCompat.FLAG_INSISTENT, true)
                // Кнопка быстрого отключения на шторке
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss",
                    dismissPendingIntent
                )
        }
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Timber.d("⏰ Будильник запущен: ID=$notificationId, Looping=true")
        } catch (e: SecurityException) {
            Timber.e(e, "❌ Ошибка запуска: нет разрешения на уведомления")
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "term_reminders"
        // Константы для обработки ручного сброса будильника
        const val ACTION_DISMISS_ALARM = "com.docs.scanner.ACTION_DISMISS_ALARM"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        // ⏰ Обработка клика по кнопке "Dismiss" (Сбросить) прямо на уведомлении
        if (action == ACTION_DISMISS_ALARM) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
                Timber.d("⏰ Будильник $notificationId успешно отключен пользователем")
            }
            return
        }
        
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        ReceiverEntryPoint::class.java
                    )
                    val database = entryPoint.appDatabase()
                    val scheduler = entryPoint.alarmScheduler()

                    val activeTerms = database.termDao().observeActive().first()
                    
                    activeTerms.forEach { termEntity ->
                        scheduler.scheduleTerm(termEntity)
                    }
                    Timber.i("✅ Восстановлено будильников после перезагрузки: ${activeTerms.size}")
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Timber.e(e, "❌ Ошибка восстановления будильников")
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val termId = intent.getLongExtra("term_id", -1)
        val title = intent.getStringExtra("title") ?: "Term Reminder"
        val description = intent.getStringExtra("description")
        val isMainAlarm = intent.getBooleanExtra("is_main_alarm", false)
        val offset = intent.getIntExtra("notification_offset", 0)
        
        showNotification(context, termId, offset, title, description, isMainAlarm)
    }