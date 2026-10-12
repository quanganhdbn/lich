package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.TripRepository
import com.example.notification.TripReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LichXeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { TripRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        TripReminderScheduler.createNotificationChannel(this)

        applicationScope.launch {
            repository.seedSampleDataIfNeeded()
        }
    }
}
