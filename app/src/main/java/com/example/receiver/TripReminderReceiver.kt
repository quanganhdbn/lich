package com.example.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.notification.TripReminderScheduler

class TripReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TripReminderReceiver"
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_PLATE = "extra_plate"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Nhắc nhở lịch chạy xe"
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: "Bạn có một chuyến xe sắp khởi hành theo lịch trình!"
        val plate = intent.getStringExtra(EXTRA_PLATE) ?: ""

        Log.d(TAG, "Trip reminder received for trip #$tripId, plate=$plate")

        TripReminderScheduler.createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_trip_id", tripId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tripId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullTitle = "🚗 $title"

        val notification = NotificationCompat.Builder(context, TripReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(fullTitle)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n\nChạm để mở ứng dụng xem chi tiết chuyến xe và cập nhật ODO."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(tripId.toInt(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing: ${e.message}")
        }
    }
}
