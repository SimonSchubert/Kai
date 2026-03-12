package com.inspiredandroid.kai.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.inspiredandroid.kai.shared.R
import java.util.concurrent.atomic.AtomicInteger

class NotificationHelper(
    private val context: Context,
    private val permissionController: NotificationPermissionController,
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationIdCounter = AtomicInteger(0)

    companion object {
        private const val CHANNEL_ID = "kai_ai_notifications"
        private const val CHANNEL_NAME = "AI Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications sent by AI assistant"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun sendNotification(
        title: String,
        message: String,
    ): NotificationResult {
        // Check and request permission if needed
        if (!permissionController.hasPermission()) {
            val granted = permissionController.requestPermission()
            if (!granted) {
                return NotificationResult.Error("Notification permission denied")
            }
        }

        return try {
            val notificationId = notificationIdCounter.incrementAndGet()

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notificationId, notification)

            NotificationResult.Success(notificationId, message)
        } catch (e: Exception) {
            NotificationResult.Error("Failed to send notification: ${e.message}")
        }
    }
}

sealed class NotificationResult {
    data class Success(val notificationId: Int, val message: String) : NotificationResult()
    data class Error(val message: String) : NotificationResult()
}
