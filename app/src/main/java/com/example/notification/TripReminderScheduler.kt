package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.receiver.TripReminderReceiver

object TripReminderScheduler {
    private const val TAG = "TripReminderScheduler"
    const val CHANNEL_ID = "trip_reminder_channel"
    private const val CHANNEL_NAME = "Nhắc nhở Lịch Chạy Xe"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Kênh thông báo nhắc nhở lịch chạy xe hàng ngày"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleTripReminder(
        context: Context,
        tripId: Long,
        reminderTimeMillis: Long,
        title: String,
        content: String,
        vehiclePlate: String = ""
    ) {
        if (reminderTimeMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "Reminder time is in the past, not scheduling: $reminderTimeMillis")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TripReminderReceiver::class.java).apply {
            putExtra(TripReminderReceiver.EXTRA_TRIP_ID, tripId)
            putExtra(TripReminderReceiver.EXTRA_TITLE, title)
            putExtra(TripReminderReceiver.EXTRA_CONTENT, content)
            putExtra(TripReminderReceiver.EXTRA_PLATE, vehiclePlate)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tripId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            }
            Log.d(TAG, "Scheduled reminder for trip #$tripId at timestamp $reminderTimeMillis")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission denied: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
        }
    }

    fun cancelTripReminder(context: Context, tripId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TripReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tripId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled reminder for trip #$tripId")
        }
    }

    fun showInstantNotification(
        context: Context,
        title: String,
        content: String,
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission POST_NOTIFICATIONS not granted: ${e.message}")
        }
    }
}
