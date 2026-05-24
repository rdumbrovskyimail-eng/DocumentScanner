package com.docs.scanner.data.local.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.docs.scanner.data.local.entity.TermEntity
import com.docs.scanner.domain.alarm.AlarmScheduler
import com.docs.scanner.domain.repository.TermRepository
import com.docs.scanner.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class TermAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var termRepo: TermRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        termRepo.getAllActive().forEach { term ->
                            if (!term.isCompleted && !term.isCancelled && term.dueDate > System.currentTimeMillis()) {
                                alarmScheduler.scheduleTerm(TermEntity.fromDomain(term))
                            }
                        }
                    } finally { pending.finish() }
                }
            }
            else -> {
                val termId = intent.getLongExtra("term_id", -1)
                if (termId <= 0) return
                val title = intent.getStringExtra("title") ?: return
                val description = intent.getStringExtra("description")
                val isMain = intent.getBooleanExtra("is_main_alarm", false)
                val offset = intent.getIntExtra("notification_offset", 0)
                showNotification(context, termId, offset, title, description, isMain)
            }
        }
    }

    private fun showNotification(
        context: Context,
        termId: Long,
        offset: Int,
        title: String,
        description: String?,
        isMainAlarm: Boolean
    ) {
        // ✅ УНИКАЛЬНЫЙ NOTIFICATION ID (каждое напоминание = отдельное уведомление)
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
        }
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            println("✅ Notification shown: ID=$notificationId, main=$isMainAlarm")
        } catch (e: SecurityException) {
            println("❌ Notification permission denied: ${e.message}")
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "term_reminders"
    }
}