package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.TripViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScheduleScreen(
    viewModel: TripViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val statusFilter by viewModel.selectedStatusFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val trips by viewModel.filteredTrips.collectAsState()
    val allTrips by viewModel.allTrips.collectAsState()
    val scheduleCount by viewModel.currentDayScheduleCount.collectAsState()
    val completedCount by viewModel.currentDayCompletedCount.collectAsState()

    // 14-day date range (-3 days to +10 days) for flexible daily schedule browsing
    val dateStripList = remember(selectedDate) {
        val list = mutableListOf<DateItem>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -3)
        val formatKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatDayNum = SimpleDateFormat("dd", Locale.getDefault())
        val formatMonth = SimpleDateFormat("MM", Locale.getDefault())

        val dayNames = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")

        for (i in 0..13) {
            val date = cal.time
            val dayOfWeekIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
            list.add(
                DateItem(
                    dateKey = formatKey.format(date),
                    dayName = dayNames[dayOfWeekIndex],
                    dayNumber = formatDayNum.format(date),
                    month = formatMonth.format(date),
                    isSunday = dayOfWeekIndex == 0
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
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
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "RidePilot",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Lịch đón tiễn theo giờ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Quick Go To Today Button
                    IconButton(
                        onClick = { viewModel.goToToday() },
                        modifier = Modifier.testTag("go_to_today_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Today,
                            contentDescription = "Hôm nay",
                            tint = NavyPrimary
                        )
                    }

                    // Quick Add Trip Button
                    IconButton(
                        onClick = { viewModel.openNewTripDialog() },
                        modifier = Modifier.testTag("add_trip_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Thêm chuyến",
                            tint = NavyPrimary
                        )
                    }

                    // Test Notification Button
                    IconButton(
                        onClick = { viewModel.sendTestNotificationNow() },
                        modifier = Modifier.testTag("test_notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Test thông báo",
                            tint = AmberAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openNewTripDialog() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Tạo Chuyến Đi", fontWeight = FontWeight.Bold) },
                containerColor = NavyPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_trip_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // PROMINENT 2026 DATE SELECTOR HEADER
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)) {
                    // Month & Navigation bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = formatMonthYearHeader(selectedDate),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = formatDateLongDisplay(selectedDate),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.goToPreviousDay() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Ngày trước", modifier = Modifier.size(22.dp))
                            }

                            FilledTonalButton(
                                onClick = { viewModel.goToToday() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Hôm nay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { viewModel.goToNextDay() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Ngày sau", modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Date Strip
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dateStripList) { dateItem ->
                            val isSelected = dateItem.dateKey == selectedDate
                            val isToday = dateItem.dateKey == viewModel.todayString
                            val countForDate = allTrips.count { it.tripDate == dateItem.dateKey }

                            ModernDateChip(
                                item = dateItem,
                                isSelected = isSelected,
                                isToday = isToday,
                                tripCount = countForDate,
                                onClick = { viewModel.selectDate(dateItem.dateKey) }
                            )
                        }
                    }
                }
            }

            // Search Bar & Filter Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Tìm theo địa điểm, khách hàng, chuyến bay...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Xóa tìm kiếm")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("search_trip_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2 TAB DUY NHẤT: LỊCH CHẠY & ĐÃ HOÀN THÀNH (Bỏ Cần Chạy & Đã Hủy)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isScheduleTab = statusFilter != "COMPLETED"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isScheduleTab) NavyPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setStatusFilter("SCHEDULE") }
                                .testTag("tab_schedule")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = if (isScheduleTab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lịch Chạy ($scheduleCount)",
                                    fontWeight = if (isScheduleTab) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isScheduleTab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val isCompletedTab = statusFilter == "COMPLETED"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCompletedTab) StatusCompleted else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setStatusFilter("COMPLETED") }
                                .testTag("tab_completed")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isCompletedTab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Đã Hoàn Thành ($completedCount)",
                                    fontWeight = if (isCompletedTab) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isCompletedTab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Trips List (Sorted strictly by pickup time startTime in ascending order)
            if (trips.isEmpty()) {
                EmptyTripView(
                    dateText = formatDateLongDisplay(selectedDate),
                    isCompletedTab = statusFilter == "COMPLETED",
                    onAddClick = { viewModel.openNewTripDialog() },
                    onSwitchToSchedule = { viewModel.setStatusFilter("SCHEDULE") }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trips, key = { it.id }) { trip ->
                        TripCard(
                            trip = trip,
                            onClick = { viewModel.openTripDetails(trip) },
                            onFinishClick = {
                                viewModel.finishTrip(trip.id)
                                android.widget.Toast.makeText(
                                    context,
                                    "✓ Đã hoàn thành chuyến đón ${trip.startTime}! Đã chuyển sang mục Đã hoàn thành.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onReminderClick = { viewModel.openQuickAction(trip, "REMINDER") }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ModernDateChip(
    item: DateItem,
    isSelected: Boolean,
    isToday: Boolean,
    tripCount: Int,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        NavyPrimary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val dayNameColor = if (isSelected) {
        contentColor.copy(alpha = 0.85f)
    } else if (item.isSunday) {
        StatusCancelled
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("date_chip_${item.dateKey}")
    ) {
        Text(
            text = item.dayName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = dayNameColor
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.dayNumber,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = contentColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (tripCount > 0) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) CyanAccent else NavyPrimary,
                modifier = Modifier.size(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (tripCount > 9) "9+" else tripCount.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else if (isToday) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else NavyPrimary)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TripCard(
    trip: Trip,
    onClick: () -> Unit,
    onFinishClick: () -> Unit,
    onReminderClick: () -> Unit
) {
    val context = LocalContext.current

    val (statusColor, statusText) = when (trip.status) {
        TripStatus.IN_PROGRESS.name -> StatusInProgress to "Đang chạy"
        TripStatus.COMPLETED.name -> StatusCompleted to "Đã xong"
        TripStatus.CANCELLED.name -> StatusCancelled to "Đã hủy"
        else -> StatusScheduled to "Lên lịch"
    }

    fun makeCall(phone: String) {
        if (phone.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (trip.status == TripStatus.IN_PROGRESS.name) StatusInProgress.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("trip_card_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Pickup Time badge + Status badge + Reminder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = NavyPrimary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Đón ${trip.startTime}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }

                    if (trip.reminderEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Có hẹn giờ nhắc",
                            tint = AmberAccent,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(onClick = onReminderClick)
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Flight Badge (if present)
            if (trip.flightNumber.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CyanAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Flight,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chuyến bay: ${trip.flightNumber} ${if (trip.flightStatus.isNotEmpty()) "• ${trip.flightStatus}" else ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CyanAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Route Timeline
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StatusInProgress))
                    Box(modifier = Modifier.width(2.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StatusCancelled))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.startLocation,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = trip.destination,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Passenger Info & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trip.customerName.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${trip.customerName} ${if (trip.customerPhone.isNotEmpty()) "• ${trip.customerPhone}" else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (trip.customerPhone.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = trip.customerPhone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (trip.customerPhone.isNotEmpty()) {
                        IconButton(
                            onClick = { makeCall(trip.customerPhone) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Gọi khách", tint = StatusInProgress, modifier = Modifier.size(18.dp))
                        }
                    }

                    // TÁC VỤ DUY NHẤT: HOÀN THÀNH
                    if (trip.status != TripStatus.COMPLETED.name) {
                        Button(
                            onClick = onFinishClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted, contentColor = Color.White),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("finish_btn_${trip.id}")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hoàn thành", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            color = StatusCompleted.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = StatusCompleted, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Đã xong", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusCompleted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyTripView(
    dateText: String,
    isCompletedTab: Boolean = false,
    onAddClick: () -> Unit,
    onSwitchToSchedule: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (isCompletedTab) StatusCompleted.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isCompletedTab) Icons.Filled.CheckCircle else Icons.Outlined.EventAvailable,
                    contentDescription = null,
                    tint = if (isCompletedTab) StatusCompleted else NavyPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isCompletedTab) "Chưa có chuyến hoàn thành" else "Chưa có lịch chạy nào",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isCompletedTab) {
                "Ngày $dateText chưa có chuyến xe nào được đánh dấu hoàn thành.\nKhi hoàn thành chuyến bên Lịch Chạy, chuyến sẽ tự chuyển vào đây."
            } else {
                "Ngày $dateText hiện đang trống lịch.\nHãy chạm nút bên dưới để tạo lịch đón tiễn mới."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isCompletedTab) {
            OutlinedButton(
                onClick = onSwitchToSchedule,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Xem Lịch Chạy Đang Chờ")
            }
        } else {
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                modifier = Modifier.testTag("empty_add_trip_btn")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tạo Lịch Chạy Mới", fontWeight = FontWeight.Bold)
            }
        }
    }
}

data class DateItem(
    val dateKey: String,
    val dayName: String,
    val dayNumber: String,
    val month: String,
    val isSunday: Boolean
)

fun formatMonthYearHeader(dateStr: String): String {
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
        if (parsed != null) {
            SimpleDateFormat("'THÁNG' MM, yyyy", Locale.getDefault()).format(parsed)
        } else ""
    } catch (_: Exception) {
        ""
    }
}

fun formatDateLongDisplay(dateStr: String): String {
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
        if (parsed != null) {
            val cal = Calendar.getInstance()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -2)
            val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

            when (dateStr) {
                todayStr -> "Hôm nay, " + SimpleDateFormat("dd 'tháng' MM", Locale.getDefault()).format(parsed)
                tomorrowStr -> "Ngày mai, " + SimpleDateFormat("dd 'tháng' MM", Locale.getDefault()).format(parsed)
                yesterdayStr -> "Hôm qua, " + SimpleDateFormat("dd 'tháng' MM", Locale.getDefault()).format(parsed)
                else -> {
                    val dayNames = arrayOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
                    val dayCal = Calendar.getInstance().apply { time = parsed }
                    val dName = dayNames[dayCal.get(Calendar.DAY_OF_WEEK) - 1]
                    "$dName, " + SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(parsed)
                }
            }
        } else dateStr
    } catch (_: Exception) {
        dateStr
    }
}

@Composable
fun DailySummaryCard(
    selectedDate: String,
    activeTrips: List<Trip>,
    completedCount: Int,
    onTripClick: (Trip) -> Unit,
    onAddTripClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalTrips = activeTrips.size + completedCount
    val nextUpcomingTrip = activeTrips.firstOrNull()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            NavyPrimary.copy(alpha = 0.12f)
        ),
        modifier = modifier.testTag("daily_summary_card")
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NavyPrimary.copy(alpha = 0.04f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(14.dp)
        ) {
            // Header Row: Title & Active Count Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavyPrimary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tổng Quan Hôm Nay",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (activeTrips.isNotEmpty()) AmberAccent.copy(alpha = 0.18f) else StatusCompleted.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (activeTrips.isNotEmpty()) AmberAccent else StatusCompleted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeTrips.isNotEmpty()) "${activeTrips.size} chuyến cần chạy" else "Hoàn thành",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeTrips.isNotEmpty()) Color(0xFFB45309) else StatusCompleted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Stats Row: Active Trips & Completed Trips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active Trips Stat Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = NavyPrimary.copy(alpha = 0.06f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activeTrips.size}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Chuyến cần đón",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Đang chờ chạy",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Completed Trips Stat Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = StatusCompleted.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusCompleted.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$completedCount",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StatusCompleted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Đã hoàn thành",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (totalTrips > 0) "${(completedCount * 100) / totalTrips}% tiến độ" else "Chưa có chuyến",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // UPCOMING PICKUPS SECTION
            if (nextUpcomingTrip != null) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Chuyến đón tiếp theo:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }

                        if (activeTrips.size > 1) {
                            Text(
                                text = "+${activeTrips.size - 1} chuyến sau",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Next Upcoming Pickup Highlight Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTripClick(nextUpcomingTrip) }
                            .testTag("upcoming_pickup_highlight")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pickup Time Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NavyPrimary
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Đón",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = nextUpcomingTrip.startTime,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Route & Passenger
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nextUpcomingTrip.startLocation,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "→ ${nextUpcomingTrip.destination}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (nextUpcomingTrip.customerName.isNotEmpty() || nextUpcomingTrip.flightNumber.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        if (nextUpcomingTrip.flightNumber.isNotEmpty()) {
                                            Text(
                                                text = "✈ ${nextUpcomingTrip.flightNumber}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = CyanAccent
                                            )
                                            if (nextUpcomingTrip.customerName.isNotEmpty()) {
                                                Text(
                                                    text = " • ",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (nextUpcomingTrip.customerName.isNotEmpty()) {
                                            Text(
                                                text = nextUpcomingTrip.customerName,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Xem chi tiết",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Subsequent upcoming pickups strip if activeTrips.size > 1
                    if (activeTrips.size > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(activeTrips.drop(1), key = { it.id }) { laterTrip ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .clickable { onTripClick(laterTrip) }
                                        .testTag("upcoming_later_trip_${laterTrip.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AccessTime,
                                            contentDescription = null,
                                            tint = NavyPrimary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${laterTrip.startTime} • ${laterTrip.destination.take(16)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (completedCount > 0) {
                // All trips completed for the day
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusCompleted.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = StatusCompleted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Đã hoàn thành toàn bộ $completedCount chuyến trong ngày! 🎉",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusCompleted
                        )
                        Text(
                            text = "Không còn chuyến đón nào đang chờ chạy",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // No trips for the day yet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chưa có chuyến đón nào lên lịch ngày này",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onAddTripClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("+ Tạo chuyến", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

