package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TripStatus(val label: String) {
    SCHEDULED("Lên lịch"),
    IN_PROGRESS("Đang chạy"),
    COMPLETED("Hoàn thành"),
    CANCELLED("Đã hủy")
}

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripDate: String, // Format "YYYY-MM-DD"
    val startTime: String, // Format "HH:mm"
    val endTime: String = "", // Format "HH:mm"
    val startLocation: String,
    val destination: String,
    val waypoints: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val status: String = TripStatus.SCHEDULED.name,
    val flightNumber: String = "", // Ví dụ "VN123" nếu là chuyến bay
    val flightStatus: String = "", // Thông tin từ AirLabs
    val reminderEnabled: Boolean = false,
    val reminderTimeMillis: Long = 0L, // Thời điểm hẹn giờ nhắc nhở
    val completedBy: String = "", // Tài khoản Gmail đã tích hoàn thành chuyến
    val completedAt: Long = 0L, // Thời gian tích hoàn thành (epoch millis)
    val createdAt: Long = System.currentTimeMillis()
)
