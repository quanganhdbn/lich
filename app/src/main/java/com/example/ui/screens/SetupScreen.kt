package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthManager
import com.example.data.SoundTone
import com.example.data.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.TripViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: TripViewModel,
    onRequestNotificationPermission: () -> Unit,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val setupState by viewModel.setupState.collectAsState()

    var dispatcherNameInput by remember(setupState.dispatcherName) { mutableStateOf(setupState.dispatcherName) }
    var dispatcherPhoneInput by remember(setupState.dispatcherPhone) { mutableStateOf(setupState.dispatcherPhone) }
    var fleetTeamInput by remember(setupState.fleetTeamName) { mutableStateOf(setupState.fleetTeamName) }

    var isEditingDispatcherInfo by remember { mutableStateOf(false) }

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
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Cài Đặt",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Chuông thông báo & Phân quyền điều xe",
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
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // MỤC 0: TÀI KHOẢN GOOGLE (GMAIL) ĐÃ ĐĂNG NHẬP
            // ==========================================
            item {
                val currentUser = AuthManager.currentUser
                var showSignOutDialog by remember { mutableStateOf(false) }

                if (showSignOutDialog) {
                    AlertDialog(
                        onDismissRequest = { showSignOutDialog = false },
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        title = { Text("Đăng xuất tài khoản?") },
                        text = { Text("Bạn có chắc chắn muốn đăng xuất tài khoản Gmail (${currentUser?.email ?: ""}) khỏi ứng dụng không?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showSignOutDialog = false
                                    onSignOut()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Đăng xuất")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSignOutDialog = false }) {
                                Text("Hủy")
                            }
                        }
                    )
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("user_account_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (currentUser?.displayName?.take(1) ?: currentUser?.email?.take(1) ?: "G").uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser?.displayName?.ifBlank { "Tài khoản Google" } ?: "Tài khoản Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SuccessGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Gmail",
                                        color = SuccessGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentUser?.email ?: "Đã đăng nhập",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Đăng xuất",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ==========================================
            // MỤC 1: SETUP CHUÔNG THÔNG BÁO ĐẨY
            // ==========================================
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("setup_notification_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Section Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AmberAccent.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Chuông Thông Báo Đẩy",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Cấu hình âm thanh chuông báo và độ rung",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = setupState.pushNotificationEnabled,
                                onCheckedChange = { viewModel.setupManager.updatePushNotificationEnabled(it) },
                                modifier = Modifier.testTag("switch_push_notif")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Danh sách loại chuông thông báo đẩy
                        Text(
                            "Chọn Giai Điệu Chuông Báo:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SoundTone.entries.forEach { tone ->
                                val isSelected = setupState.selectedSound == tone
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) NavyPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) NavyPrimary else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setupManager.updateSelectedSound(tone)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.setupManager.updateSelectedSound(tone) },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tone.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = tone.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Nút Nghe Thử Chuông
                                        IconButton(
                                            onClick = { viewModel.setupManager.previewSoundTone(tone) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.VolumeUp,
                                                contentDescription = "Nghe thử chuông",
                                                tint = if (isSelected) NavyPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Toggle Rung
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Vibration,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Rung khi có chuông báo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Rung nhịp đôi rõ ràng cho tài xế", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = setupState.vibrationEnabled,
                                onCheckedChange = { viewModel.setupManager.updateVibrationEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Thời gian hẹn giờ nhắc mặc định
                        Text(
                            "Thời Gian Nhắc Trước Giờ Đón Mặc Định:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(15, 30, 60, 120).forEach { mins ->
                                val isSelected = setupState.defaultReminderMinutes == mins
                                val label = if (mins < 60) "${mins}p" else "${mins / 60}h"
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setupManager.updateDefaultReminderMinutes(mins) },
                                    label = { Text("Trước $label", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Toggle nhắc khi máy bay hạ cánh
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Filled.FlightLand,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Tự động báo khi máy bay hạ cánh", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Cập nhật giờ đón theo giờ hạ cánh thực tế", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = setupState.autoFlightLandingReminder,
                                onCheckedChange = { viewModel.setupManager.updateAutoFlightLandingReminder(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Nút test chuông thông báo đẩy thực tế
                        Button(
                            onClick = {
                                onRequestNotificationPermission()
                                viewModel.setupManager.previewSoundTone()
                                viewModel.sendTestNotificationNow()
                                Toast.makeText(context, "🔔 Đã gửi chuông thông báo thử nghiệm!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_test_notification")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bấm Thử Chuông Thông Báo Đẩy Ngay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ==========================================
            // MỤC 2: SETUP PHÂN QUYỀN NGƯỜI ĐIỀU XE
            // ==========================================
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("setup_dispatcher_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Section Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NavyPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Phân Quyền Người Điều Xe",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Quyền hạn điều phối, tạo lịch & chốt chuyến",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Chọn vai trò
                        Text(
                            "Vai Trò Hiện Tại Trên Máy Này:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserRole.entries.forEach { role ->
                                val isSelected = setupState.currentRole == role
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) NavyPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setupManager.updateCurrentRole(role) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = if (role == UserRole.DISPATCHER) Icons.Filled.Badge else Icons.Filled.DirectionsCar,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (role == UserRole.DISPATCHER) "Người Điều Xe" else "Tài Xế Lái Xe",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Thông tin người điều phối xe
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Thông Tin Người Điều Phối",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(
                                        onClick = {
                                            if (isEditingDispatcherInfo) {
                                                viewModel.setupManager.updateDispatcherInfo(
                                                    dispatcherNameInput.trim(),
                                                    dispatcherPhoneInput.trim(),
                                                    fleetTeamInput.trim()
                                                )
                                                Toast.makeText(context, "Đã lưu thông tin người điều xe!", Toast.LENGTH_SHORT).show()
                                            }
                                            isEditingDispatcherInfo = !isEditingDispatcherInfo
                                        }
                                    ) {
                                        Text(if (isEditingDispatcherInfo) "Lưu" else "Chỉnh Sửa", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isEditingDispatcherInfo) {
                                    OutlinedTextField(
                                        value = dispatcherNameInput,
                                        onValueChange = { dispatcherNameInput = it },
                                        label = { Text("Họ tên người điều xe") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = dispatcherPhoneInput,
                                        onValueChange = { dispatcherPhoneInput = it },
                                        label = { Text("Hotline điều phối") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = fleetTeamInput,
                                        onValueChange = { fleetTeamInput = it },
                                        label = { Text("Đội xe phụ trách") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Person, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(setupState.dispatcherName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Phone, contentDescription = null, tint = StatusInProgress, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Hotline: ${setupState.dispatcherPhone}", fontSize = 13.sp)
                                        }
                                        if (setupState.dispatcherPhone.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${setupState.dispatcherPhone}"))
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Filled.Call, contentDescription = "Gọi điều xe", tint = StatusInProgress, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(setupState.fleetTeamName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Ma trận cấu hình quyền hạn
                        Text(
                            "Ma Trận Phân Quyền Thao Tác:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val permissions = listOf(
                            Triple("Tạo & phân cuốc mới cho tài xế", setupState.canCreateTrips) { v: Boolean ->
                                viewModel.setupManager.updatePermission(canCreate = v)
                            },
                            Triple("Sửa đổi giờ đón & lộ trình xe", setupState.canEditRouteTime) { v: Boolean ->
                                viewModel.setupManager.updatePermission(canEdit = v)
                            },
                            Triple("Hủy hoặc chuyển giao cuốc xe", setupState.canCancelReassign) { v: Boolean ->
                                viewModel.setupManager.updatePermission(canCancel = v)
                            },
                            Triple("Duyệt & xác nhận hoàn thành", setupState.canApproveComplete) { v: Boolean ->
                                viewModel.setupManager.updatePermission(canApprove = v)
                            },
                            Triple("Xem cước phí & doanh thu chuyến", setupState.canViewFareRevenue) { v: Boolean ->
                                viewModel.setupManager.updatePermission(canViewRev = v)
                            },
                            Triple("Chuông báo khi tài xế tích xong chuyến", setupState.notifyOnTripFinished) { v: Boolean ->
                                viewModel.setupManager.updatePermission(notifyFinished = v)
                            }
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            permissions.forEach { (label, value, onToggle) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = value,
                                        onCheckedChange = { onToggle(it) },
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Khóa PIN bảo vệ cấu hình
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Khóa bảo vệ bằng mã PIN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Yêu cầu PIN khi thay đổi phân quyền", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = setupState.isPinProtectionEnabled,
                                    onCheckedChange = {
                                        viewModel.setupManager.updatePinProtection(it)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.padding(0.dp) // dummy or native layout modifier
)
