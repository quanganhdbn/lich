package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TripStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.TripViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: TripViewModel,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allTrips by viewModel.allTrips.collectAsState()
    val todayString = viewModel.todayString

    val totalTrips = allTrips.size
    val todayTrips = allTrips.filter { it.tripDate == todayString }
    val completedTrips = allTrips.filter { it.status == TripStatus.COMPLETED.name }
    val inProgressTrips = allTrips.filter { it.status == TripStatus.IN_PROGRESS.name }
    val scheduledTrips = allTrips.filter { it.status == TripStatus.SCHEDULED.name }
    val cancelledTrips = allTrips.filter { it.status == TripStatus.CANCELLED.name }

    val flightTrips = allTrips.filter { it.flightNumber.isNotEmpty() }
    val reminderTrips = allTrips.filter { it.reminderEnabled }

    val completionRate = if (totalTrips > 0) {
        ((completedTrips.size.toFloat() / totalTrips.toFloat()) * 100).toInt()
    } else 0

    // Time of day distribution
    val morningTrips = allTrips.count {
        val h = it.startTime.take(2).toIntOrNull() ?: 8
        h in 5..11
    }
    val afternoonTrips = allTrips.count {
        val h = it.startTime.take(2).toIntOrNull() ?: 14
        h in 12..17
    }
    val eveningTrips = allTrips.count {
        val h = it.startTime.take(2).toIntOrNull() ?: 19
        h >= 18 || h < 5
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = NavyPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Insights,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Thống Kê Lịch Chạy",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Tổng quan hoạt động & lịch trình",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Hero Summary Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(NavyPrimary.copy(alpha = 0.07f), CyanAccent.copy(alpha = 0.05f))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Tiến độ hoàn thành",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$completionRate%",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NavyPrimary
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = NavyPrimary.copy(alpha = 0.12f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { completionRate / 100f },
                                        modifier = Modifier.size(54.dp),
                                        color = NavyPrimary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                                        strokeWidth = 6.dp
                                    )
                                    Text(
                                        text = "${completedTrips.size}/$totalTrips",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hôm nay", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${todayTrips.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Đang chạy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${inProgressTrips.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StatusInProgress)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sắp tới", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${scheduledTrips.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StatusScheduled)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Đã xong", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${completedTrips.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StatusCompleted)
                            }
                        }
                    }
                }
            }

            // Key KPI Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Airport flights card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Surface(shape = CircleShape, color = CyanAccent.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Flight, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Chuyến Bay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${flightTrips.size} chuyến", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        }
                    }

                    // Active reminders card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Surface(shape = CircleShape, color = AmberAccent.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Đặt Nhắc Nhở", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${reminderTrips.size} chuyến", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                        }
                    }
                }
            }

            // Time Distribution Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Phân Bổ Theo Khung Giờ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val totalCount = if (totalTrips > 0) totalTrips else 1

                        TimeDistributionRow(
                            label = "Buổi sáng (05:00 - 12:00)",
                            count = morningTrips,
                            ratio = morningTrips.toFloat() / totalCount,
                            color = StatusScheduled
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TimeDistributionRow(
                            label = "Buổi chiều (12:00 - 18:00)",
                            count = afternoonTrips,
                            ratio = afternoonTrips.toFloat() / totalCount,
                            color = AmberAccent
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TimeDistributionRow(
                            label = "Buổi tối & đêm (18:00 - 05:00)",
                            count = eveningTrips,
                            ratio = eveningTrips.toFloat() / totalCount,
                            color = StatusCompleted
                        )
                    }
                }
            }

            // Notification System Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hệ Thống Thông Báo Nhắc Lịch", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Ứng dụng tự động lập lịch báo trước giờ đón khách chính xác qua Notification. Bấm nút dưới để thử nghiệm nhận thông báo ngay trên thanh trạng thái.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onRequestNotificationPermission,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_push_notification_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thử Nghiệm Phát Thông Báo Ngay", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TimeDistributionRow(
    label: String,
    count: Int,
    ratio: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("$count chuyến", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
