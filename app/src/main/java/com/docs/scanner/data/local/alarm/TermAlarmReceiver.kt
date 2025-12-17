package com.docs.scanner.data.local.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.docs.scanner.presentation.MainActivity

class TermAlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val termId = intent.getLongExtra("term_id", -1)
        val title = intent.getStringExtra("title") ?: "Term Reminder"
        val description = intent.getStringExtra("description")
        val isMainAlarm = intent.getBooleanExtra("is_main_alarm", false)
        
        showNotification(context, termId, title, description, isMainAlarm)
    }
    
    private fun showNotification(
        context: Context,
        termId: Long,
        title: String,
        description: String?,
        isMainAlarm: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        
        // Создаем канал уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (isMainAlarm) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Term Reminders",
                importance
            ).apply {
                enableVibration(isMainAlarm)
                if (isMainAlarm) {
                    vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_term", termId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            termId.toInt(),
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
        
        notificationManager.notify(termId.toInt(), builder.build())
    }
    
    companion object {
        private const val CHANNEL_ID = "term_reminders"
    }
}
