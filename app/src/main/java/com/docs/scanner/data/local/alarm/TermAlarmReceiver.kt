package com.docs.scanner.data.local.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.docs.scanner.presentation.MainActivity

class TermAlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val termId = intent.getLongExtra("term_id", -1)
        val title = intent.getStringExtra("title") ?: "Term Reminder"
        val description = intent.getStringExtra("description")
        val isMainAlarm = intent.getBooleanExtra("is_main_alarm", false)
        val offset = intent.getIntExtra("notification_offset", 0) // ✅ Уникальный offset
        
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
        // ✅ УНИКАЛЬНЫЙ NOTIFICATION ID (каждое напоминание = отдельное уведомление)
        val notificationId = (termId.toInt() * 1000 + offset)
        
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