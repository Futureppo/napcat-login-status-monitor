package com.napcat.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    // 前台任务通知渠道 ID（低重要级、无声）
    const val FOREGROUND_CHANNEL_ID = "foreground_task"
    // API 告警通知渠道 ID（高重要级、有声/震动）
    const val ALERT_CHANNEL_ID = "api_alerts"

    private const val FOREGROUND_NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_ID = 2001

    // 创建两个通知渠道（Android 8.0+）
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val foregroundChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Foreground Task",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                description = "Foreground worker status"
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "API Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                description = "API availability alerts"
            }

            nm.createNotificationChannel(foregroundChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    // 构建前台服务的临时通知（用于 Worker setForeground）
    fun buildForegroundNotification(context: Context): Notification {
        ensureChannels(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在检查 API 状态…")
            .setContentText("后台任务运行中")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    // 发送 API 宕机通知
    fun sendApiDownNotification(context: Context) {
        ensureChannels(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("API 故障警报")
            .setContentText("监控的 API 目前不可用")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
    }
}


