package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.network.AirlabsService
import com.example.network.FlightInfo
import com.example.network.GeminiService
import com.example.network.ParsedTripData
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun extractFlightLandingTime(rawTime: String): String? {
    if (rawTime.isBlank()) return null
    val match = Regex("""\b(\d{1,2}:\d{2})\b""").find(rawTime)
    if (match != null) {
        val parts = match.groupValues[1].split(":")
        val h = parts[0].padStart(2, '0')
        val m = parts[1]
        return "$h:$m"
    }
    return null
}

fun addMinutesToTimeString(timeStr: String, minutesToAdd: Int): String {
    return try {
        val parts = timeStr.trim().split(":")
        val totalMinutes = (parts[0].toInt() * 60 + parts[1].toInt() + minutesToAdd).mod(24 * 60)
        val newH = (totalMinutes / 60).toString().padStart(2, '0')
        val newM = (totalMinutes % 60).toString().padStart(2, '0')
        "$newH:$newM"
    } catch (_: Exception) {
        timeStr
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTripDialog(
    tripToEdit: Trip?,
    defaultDate: String,
    initialAiOpen: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Trip) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var tripDate by remember { mutableStateOf(tripToEdit?.tripDate ?: defaultDate) }
    var startTime by remember { mutableStateOf(tripToEdit?.startTime ?: "08:30") }
    var startLocation by remember { mutableStateOf(tripToEdit?.startLocation ?: "") }
    var destination by remember { mutableStateOf(tripToEdit?.destination ?: "") }
    var customerName by remember { mutableStateOf(tripToEdit?.customerName ?: "") }
    var customerPhone by remember { mutableStateOf(tripToEdit?.customerPhone ?: "") }
    var flightNumber by remember { mutableStateOf(tripToEdit?.flightNumber ?: "") }
    var flightStatus by remember { mutableStateOf(tripToEdit?.flightStatus ?: "") }
    var reminderEnabled by remember { mutableStateOf(tripToEdit?.reminderEnabled ?: false) }
    var reminderTimeMillis by remember { mutableStateOf(tripToEdit?.reminderTimeMillis ?: 0L) }
    var status by remember { mutableStateOf(tripToEdit?.status ?: TripStatus.SCHEDULED.name) }

    // Integrated AI Quick Input State
    var isAiCardExpanded by remember { mutableStateOf(initialAiOpen || tripToEdit == null) }
    var aiPromptInput by remember { mutableStateOf("") }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiSuccessBanner by remember { mutableStateOf(false) }

    // Inline Flight Lookup State
    var isLookingUpFlight by remember { mutableStateOf(false) }
    var inlineFlightInfo by remember { mutableStateOf<FlightInfo?>(null) }
    var flightLookupError by remember { mutableStateOf<String?>(null) }

    fun performInlineFlightLookup(code: String) {
        if (code.isBlank()) return
        isLookingUpFlight = true
        flightLookupError = null
        coroutineScope.launch {
            try {
                val info = AirlabsService.getFlightInfo(code)
                inlineFlightInfo = info
                flightStatus = "${info.airlineName} • ${info.status}"

                // AUTO-FILL LANDING TIME INTO PICKUP TIME:
                val landingTime = extractFlightLandingTime(info.arrTime)
                if (landingTime != null) {
                    startTime = landingTime
                    // Recalculate reminder
                    if (reminderEnabled) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val tripCalendar = Calendar.getInstance()
                            val parsedTime = sdf.parse("$tripDate $startTime")
                            if (parsedTime != null) {
                                tripCalendar.time = parsedTime
                                tripCalendar.add(Calendar.MINUTE, -30)
                                reminderTimeMillis = tripCalendar.timeInMillis
                            }
                        } catch (_: Exception) {}
                    }
                    Toast.makeText(
                        context,
                        "✈️ Chuyến ${info.flightIata}: Hạ cánh $landingTime ➔ Đã tự nhập Giờ đón!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // If pickup location is blank or generic, auto-fill with the arrival airport
                if ((startLocation.isBlank() || startLocation == "Điểm đón" || startLocation == "Hà Nội") && info.arrAirportName.isNotBlank()) {
                    startLocation = info.arrAirportName
                }
            } catch (e: Exception) {
                flightLookupError = e.message ?: "Không tìm thấy thông tin chuyến bay"
            } finally {
                isLookingUpFlight = false
            }
        }
    }

    fun applyParsedData(data: ParsedTripData, showToast: Boolean = true) {
        if (data.tripDate.isNotEmpty()) tripDate = data.tripDate
        if (data.startTime.isNotEmpty()) startTime = data.startTime
        if (data.startLocation.isNotEmpty()) startLocation = data.startLocation
        if (data.destination.isNotEmpty()) destination = data.destination
        if (data.customerName.isNotEmpty()) customerName = data.customerName
        if (data.customerPhone.isNotEmpty()) customerPhone = data.customerPhone
        if (data.flightNumber.isNotEmpty()) {
            flightNumber = data.flightNumber
            performInlineFlightLookup(data.flightNumber)
        }

        // Auto enable reminder for convenience
        reminderEnabled = true
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val tripCalendar = Calendar.getInstance()
            val parsedTime = sdf.parse("$tripDate $startTime")
            if (parsedTime != null) {
                tripCalendar.time = parsedTime
                tripCalendar.add(Calendar.MINUTE, -30)
                reminderTimeMillis = tripCalendar.timeInMillis
            }
        } catch (_: Exception) {}

        aiSuccessBanner = true
        if (showToast) {
            Toast.makeText(
                context,
                "✨ Đã tự động điền: $startTime • $startLocation ➔ $destination",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun triggerAiParse() {
        if (aiPromptInput.isBlank()) {
            Toast.makeText(context, "Vui lòng nhập hoặc dán nội dung lịch chạy", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Instantly parse locally & populate all fields (<1ms)
        val localData = GeminiService.parseLocally(aiPromptInput)
        applyParsedData(localData, showToast = true)

        // 2. Refine via Gemini in background if available
        isAiAnalyzing = true
        coroutineScope.launch {
            try {
                val result = GeminiService.parseScheduleFromPrompt(aiPromptInput)
                result.onSuccess { data ->
                    applyParsedData(data, showToast = false)
                }
            } finally {
                isAiAnalyzing = false
            }
        }
    }

    // Speech Recognition Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                aiPromptInput = spokenText
                val localData = GeminiService.parseLocally(spokenText)
                applyParsedData(localData, showToast = true)
            }
        }
    }

    fun launchSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói thông tin lịch chuyến xe...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Thiết bị không hỗ trợ nhận diện giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank()) {
                aiPromptInput = text
                val localData = GeminiService.parseLocally(text)
                applyParsedData(localData, showToast = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(NavyPrimary.copy(alpha = 0.08f), CyanAccent.copy(alpha = 0.08f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = NavyPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (tripToEdit == null) Icons.Filled.AddRoad else Icons.Filled.EditRoad,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (tripToEdit == null) "Tạo Lịch Chạy Mới" else "Chỉnh Sửa Lịch Chạy",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_trip_dialog_btn")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECTION 1: AI Quick Input Card (Prominent 2026 feature)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAiCardExpanded) CyanAccent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isAiCardExpanded = !isAiCardExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = CyanAccent.copy(alpha = 0.2f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = CyanAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Nhập Nhanh Bằng AI",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Dán tin nhắn hoặc nói để tự động điền form",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isAiCardExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = isAiCardExpanded) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    OutlinedTextField(
                                        value = aiPromptInput,
                                        onValueChange = { aiPromptInput = it },
                                        placeholder = {
                                            Text(
                                                "Ví dụ: 8h sáng mai đón anh Tuấn 0903123456 ở Landmark 72 ra Nội Bài bay VN123...",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ai_prompt_inline_input"),
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = 2,
                                        maxLines = 4,
                                        trailingIcon = {
                                            Row(modifier = Modifier.padding(end = 4.dp)) {
                                                IconButton(
                                                    onClick = { launchSpeechInput() },
                                                    modifier = Modifier.testTag("mic_inline_btn")
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Mic,
                                                        contentDescription = "Giọng nói",
                                                        tint = CyanAccent
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { pasteFromClipboard() },
                                                    modifier = Modifier.testTag("paste_inline_btn")
                                                ) {
                                                    Icon(
                                                        Icons.Filled.ContentPaste,
                                                        contentDescription = "Dán",
                                                        tint = NavyPrimary
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Quick Sample Chips for 1-tap testing
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val sample1 = "8h sáng mai đón anh Tuấn 0903123456 ở Keangnam Landmark 72 ra Sân bay Nội Bài bay chuyến VN123"
                                        val sample2 = "Mai 14h đón chị Linh 0988776655 từ Khách sạn Daewoo đi Bắc Ninh"
                                        val sample3 = "Đón chuyến bay VJ162 hạ cánh Sân bay Nội Bài về Times City"

                                        SuggestionChip(
                                            onClick = {
                                                aiPromptInput = sample1
                                                applyParsedData(GeminiService.parseLocally(sample1), showToast = true)
                                            },
                                            label = { Text("Mẫu 1: Keangnam ➔ Nội Bài (VN123)", fontSize = 11.sp) }
                                        )
                                        SuggestionChip(
                                            onClick = {
                                                aiPromptInput = sample2
                                                applyParsedData(GeminiService.parseLocally(sample2), showToast = true)
                                            },
                                            label = { Text("Mẫu 2: Daewoo ➔ Bắc Ninh", fontSize = 11.sp) }
                                        )
                                        SuggestionChip(
                                            onClick = {
                                                aiPromptInput = sample3
                                                applyParsedData(GeminiService.parseLocally(sample3), showToast = true)
                                            },
                                            label = { Text("Mẫu 3: Đón chuyến VJ162", fontSize = 11.sp) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { triggerAiParse() },
                                        enabled = !isAiAnalyzing && aiPromptInput.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyanAccent,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("parse_ai_inline_btn")
                                    ) {
                                        if (isAiAnalyzing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Đang phân tích...")
                                        } else {
                                            Icon(
                                                Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("✨ AI Phân Tích & Tự Động Điền", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (aiSuccessBanner) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = StatusInProgress.copy(alpha = 0.12f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusInProgress.copy(alpha = 0.35f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = StatusInProgress,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "Đã tự động trích xuất & điền vào các ô bên dưới:",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = StatusInProgress
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("• Ngày: $tripDate • Giờ đón: $startTime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Text("• Điểm đón: $startLocation ➔ Điểm đến: $destination", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                                if (customerName.isNotEmpty() || customerPhone.isNotEmpty()) {
                                                    Text("• Khách: $customerName ${if (customerPhone.isNotEmpty()) "($customerPhone)" else ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                if (flightNumber.isNotEmpty()) {
                                                    Text("• Chuyến bay: $flightNumber (Đã kích hoạt tra cứu giờ hạ cánh)", fontSize = 11.sp, color = CyanAccent)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: Lộ Trình (Điểm Đón ➔ Điểm Đến)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Route,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Lộ Trình Đón Tiễn",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = startLocation,
                                onValueChange = { startLocation = it },
                                label = { Text("Điểm đón (Khởi hành) *") },
                                placeholder = { Text("Ví dụ: Keangnam Landmark 72, Hà Nội") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.RadioButtonChecked,
                                        contentDescription = null,
                                        tint = StatusInProgress
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trip_start_location_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = destination,
                                onValueChange = { destination = it },
                                label = { Text("Điểm đến (Nơi trả khách) *") },
                                placeholder = { Text("Ví dụ: Sân bay Nội Bài (Ga T2)") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = StatusCancelled
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trip_destination_input")
                            )

                            // Quick Location chips
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SuggestionChip(
                                    onClick = {
                                        if (startLocation.isEmpty()) startLocation = "Hà Nội"
                                        else destination = "Sân bay Nội Bài"
                                    },
                                    label = { Text("Sân bay Nội Bài", fontSize = 11.sp) }
                                )
                                SuggestionChip(
                                    onClick = { destination = "KCN Yên Phong" },
                                    label = { Text("KCN Yên Phong", fontSize = 11.sp) }
                                )
                                SuggestionChip(
                                    onClick = { startLocation = "Landmark 72" },
                                    label = { Text("Landmark 72", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // SECTION 3: Thời Gian (Ngày & Giờ Đón)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Thời Gian Đón Khách",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = tripDate,
                                onValueChange = { tripDate = it },
                                label = { Text("Ngày đón (YYYY-MM-DD)") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.CalendarToday,
                                        contentDescription = null,
                                        tint = NavyPrimary
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trip_date_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = startTime,
                                onValueChange = { startTime = it },
                                label = { Text("Giờ đón khách (HH:mm) *") },
                                placeholder = { Text("08:30") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.AccessTime,
                                        contentDescription = null,
                                        tint = StatusInProgress
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trip_start_time_input")
                            )

                            // Quick Time Presets
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("07:00", "08:30", "13:30", "18:00").forEach { t ->
                                    AssistChip(
                                        onClick = { startTime = t },
                                        label = { Text(t, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 4: Mã Chuyến Bay (Tích hợp tra cứu AirLabs)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Flight,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Tra Cứu Chuyến Bay (Nếu có)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = flightNumber,
                                    onValueChange = { flightNumber = it.uppercase() },
                                    label = { Text("Mã chuyến bay") },
                                    placeholder = { Text("VD: VN123, VJ162") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("trip_flight_number_input")
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { performInlineFlightLookup(flightNumber) },
                                    enabled = !isLookingUpFlight && flightNumber.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                    modifier = Modifier.testTag("inline_flight_lookup_btn")
                                ) {
                                    if (isLookingUpFlight) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Tra Cứu")
                                    }
                                }
                            }

                            // Flight lookup detailed display with Auto Landing Time
                            if (inlineFlightInfo != null) {
                                val info = inlineFlightInfo!!
                                val landingTime = extractFlightLandingTime(info.arrTime)
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.Flight,
                                                    contentDescription = null,
                                                    tint = CyanAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "${info.airlineName} • ${info.flightIata}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = StatusInProgress.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = info.status,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = StatusInProgress,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Cất cánh (${info.depIata})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(info.depTime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(info.depAirportName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = CyanAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Hạ cánh (${info.arrIata})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    info.arrTime,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = StatusInProgress
                                                )
                                                Text(info.arrAirportName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        if (landingTime != null) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = StatusInProgress,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Đã tự nhập giờ hạ cánh ($landingTime) vào Giờ đón!",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StatusInProgress
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Thời gian đón bao gồm lấy hành lý:",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                AssistChip(
                                                    onClick = { startTime = landingTime },
                                                    label = { Text("Đúng giờ ($landingTime)", fontSize = 10.sp) }
                                                )
                                                AssistChip(
                                                    onClick = { startTime = addMinutesToTimeString(landingTime, 15) },
                                                    label = { Text("+15p (${addMinutesToTimeString(landingTime, 15)})", fontSize = 10.sp) }
                                                )
                                                AssistChip(
                                                    onClick = { startTime = addMinutesToTimeString(landingTime, 30) },
                                                    label = { Text("+30p (${addMinutesToTimeString(landingTime, 30)})", fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (flightStatus.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CyanAccent.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.FlightTakeoff,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = flightStatus,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CyanAccent
                                        )
                                    }
                                }
                            }

                            if (flightLookupError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = flightLookupError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // SECTION 5: Thông Tin Khách Hàng
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Thông Tin Khách Hàng",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = customerName,
                                    onValueChange = { customerName = it },
                                    label = { Text("Tên khách hàng") },
                                    placeholder = { Text("Anh Tuấn") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("trip_customer_name_input")
                                )

                                OutlinedTextField(
                                    value = customerPhone,
                                    onValueChange = { customerPhone = it },
                                    label = { Text("Số điện thoại") },
                                    placeholder = { Text("0903123456") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("trip_customer_phone_input")
                                )
                            }
                        }
                    }

                    // SECTION 6: Nhắc Nhở Thông Báo & Trạng Thái
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Thông báo nhắc nhở", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Báo trước giờ đón khách", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = reminderEnabled,
                                    onCheckedChange = { reminderEnabled = it },
                                    modifier = Modifier.testTag("trip_reminder_switch")
                                )
                            }

                            if (reminderEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Thời điểm nhắc trước:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(15 to "15 phút", 30 to "30 phút", 60 to "1 giờ", 120 to "2 giờ").forEach { (mins, label) ->
                                        AssistChip(
                                            onClick = {
                                                try {
                                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                                    val cal = Calendar.getInstance()
                                                    val parsed = sdf.parse("$tripDate $startTime")
                                                    if (parsed != null) {
                                                        cal.time = parsed
                                                        cal.add(Calendar.MINUTE, -mins)
                                                        reminderTimeMillis = cal.timeInMillis
                                                        Toast.makeText(context, "Sẽ nhắc lúc ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (_: Exception) {}
                                            },
                                            label = { Text(label, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))

                            // Status selection
                            Text("Trạng thái chuyến đi:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TripStatus.values().forEach { st ->
                                    val isSelected = status == st.name
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { status = st.name },
                                        label = { Text(st.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Bottom Save Action Bar
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_trip_form_btn")
                        ) {
                            Text("Hủy")
                        }

                        Button(
                            onClick = {
                                if (startLocation.isBlank() || destination.isBlank() || startTime.isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập điểm đón, điểm đến và giờ đón", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val finalTrip = (tripToEdit ?: Trip(
                                    tripDate = tripDate,
                                    startTime = startTime,
                                    startLocation = startLocation,
                                    destination = destination
                                )).copy(
                                    tripDate = tripDate,
                                    startTime = startTime,
                                    endTime = "",
                                    startLocation = startLocation.trim(),
                                    destination = destination.trim(),
                                    waypoints = "",
                                    customerName = customerName.trim(),
                                    customerPhone = customerPhone.trim(),
                                    status = status,
                                    flightNumber = flightNumber.trim(),
                                    flightStatus = flightStatus.trim(),
                                    reminderEnabled = reminderEnabled,
                                    reminderTimeMillis = if (reminderEnabled && reminderTimeMillis == 0L) {
                                        try {
                                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                            val cal = Calendar.getInstance()
                                            val parsed = sdf.parse("$tripDate $startTime")
                                            if (parsed != null) {
                                                cal.time = parsed
                                                cal.add(Calendar.MINUTE, -30)
                                                cal.timeInMillis
                                            } else {
                                                System.currentTimeMillis() + 1800000L
                                            }
                                        } catch (_: Exception) {
                                            System.currentTimeMillis() + 1800000L
                                        }
                                    } else {
                                        reminderTimeMillis
                                    }
                                )
                                onSave(finalTrip)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("save_trip_btn")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lưu Lịch Chạy", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsBottomSheet(
    trip: Trip,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStartTrip: () -> Unit,
    onFinishTrip: () -> Unit,
    onCancelTrip: () -> Unit = {},
    onReminderClick: () -> Unit = {},
    onReopenTrip: () -> Unit = {}
) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    fun openGoogleMaps(origin: String, destination: String) {
        try {
            val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Không thể mở Google Maps", Toast.LENGTH_SHORT).show()
        }
    }

    fun makePhoneCall(phone: String) {
        if (phone.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Không thể mở ứng dụng gọi", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendSms(phone: String) {
        if (phone.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone"))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Không thể mở ứng dụng tin nhắn", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyTripToClipboard() {
        val summaryText = buildString {
            appendLine("🚗 LỊCH CHẠY XE:")
            appendLine("• Ngày: ${trip.tripDate}")
            appendLine("• Giờ đón: ${trip.startTime}")
            appendLine("• Điểm đón: ${trip.startLocation}")
            appendLine("• Điểm đến: ${trip.destination}")
            if (trip.flightNumber.isNotEmpty()) appendLine("• Chuyến bay: ${trip.flightNumber} (${trip.flightStatus})")
            if (trip.customerName.isNotEmpty()) appendLine("• Khách: ${trip.customerName}")
            if (trip.customerPhone.isNotEmpty()) appendLine("• SĐT: ${trip.customerPhone}")
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Lịch Chạy Xe", summaryText))
        Toast.makeText(context, "Đã sao chép lịch chạy vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
    }

    val (statusColor, statusText) = when (trip.status) {
        TripStatus.IN_PROGRESS.name -> StatusInProgress to "Đang chạy"
        TripStatus.COMPLETED.name -> StatusCompleted to "Đã hoàn thành"
        TripStatus.CANCELLED.name -> StatusCancelled to "Đã hủy"
        else -> StatusScheduled to "Lên lịch"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Status & Time Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Đón ${trip.startTime}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Route Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(StatusInProgress))
                            Box(modifier = Modifier.width(2.dp).height(38.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(StatusCancelled))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("ĐIỂM ĐÓN", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(trip.startLocation, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

                            Spacer(modifier = Modifier.height(14.dp))

                            Text("ĐIỂM ĐẾN", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(trip.destination, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { openGoogleMaps(trip.startLocation, trip.destination) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary.copy(alpha = 0.12f), contentColor = NavyPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mở chỉ đường Google Maps", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // Flight Information (if applicable)
            if (trip.flightNumber.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyanAccent.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = CyanAccent.copy(alpha = 0.2f), modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Flight, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Chuyến bay: ${trip.flightNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyanAccent)
                            if (trip.flightStatus.isNotEmpty()) {
                                Text(trip.flightStatus, fontSize = 12.sp, color = CyanAccent.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }

            // Customer Info Card
            if (trip.customerName.isNotEmpty() || trip.customerPhone.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (trip.customerName.isNotEmpty()) trip.customerName else "Khách hàng",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        if (trip.customerPhone.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Số điện thoại: ${trip.customerPhone}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { makePhoneCall(trip.customerPhone) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusInProgress),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gọi Điện", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = { sendSms(trip.customerPhone) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Nhắn Tin", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Share / Copy button
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { copyTripToClipboard() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sao chép thông tin gửi Zalo / SMS", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (trip.status != TripStatus.COMPLETED.name) {
                    Button(
                        onClick = onFinishTrip,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hoàn Thành", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onReopenTrip,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Về Lịch Chạy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sửa Lịch", fontWeight = FontWeight.SemiBold)
                }

                IconButton(
                    onClick = onReminderClick,
                    modifier = Modifier.background(AmberAccent.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = "Nhắc nhở", tint = AmberAccent)
                }

                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Xác nhận xóa chuyến đi?") },
            text = { Text("Lịch chạy từ '${trip.startLocation}' đến '${trip.destination}' sẽ bị xóa vĩnh viễn.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa Ngay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun QuickStartTripDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = StatusInProgress, modifier = Modifier.size(32.dp))
        },
        title = { Text("Bắt đầu đón khách?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Chuyển trạng thái sang ĐANG CHẠY cho lịch đón lúc ${trip.startTime} từ ${trip.startLocation} ➔ ${trip.destination}.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusInProgress)
            ) {
                Text("Bắt Đầu Ngay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun QuickFinishTripDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusCompleted, modifier = Modifier.size(32.dp))
        },
        title = { Text("Hoàn thành chuyến đi?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Xác nhận đã trả khách an toàn tại '${trip.destination}'.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted)
            ) {
                Text("Xác Nhận Xong")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
fun SetReminderDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onSetReminder: (Long) -> Unit,
    onCancelReminder: () -> Unit
) {
    val context = LocalContext.current
    var selectedMinutesBefore by remember { mutableStateOf(30) }

    fun calculateReminderTimeMillis(minutesBefore: Int): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val cal = Calendar.getInstance()
            val parsed = sdf.parse("${trip.tripDate} ${trip.startTime}")
            if (parsed != null) {
                cal.time = parsed
                cal.add(Calendar.MINUTE, -minutesBefore)
                cal.timeInMillis
            } else {
                System.currentTimeMillis() + 1800000L
            }
        } catch (_: Exception) {
            System.currentTimeMillis() + 1800000L
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(32.dp))
        },
        title = { Text("Cài đặt nhắc nhở", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Chọn thời gian gửi thông báo trước giờ đón (${trip.startTime}):")
                Spacer(modifier = Modifier.height(12.dp))
                listOf(15 to "Trước 15 phút", 30 to "Trước 30 phút", 60 to "Trước 1 giờ", 120 to "Trước 2 giờ").forEach { (mins, text) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMinutesBefore = mins }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMinutesBefore == mins,
                            onClick = { selectedMinutesBefore = mins }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetMillis = calculateReminderTimeMillis(selectedMinutesBefore)
                    onSetReminder(targetMillis)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Lưu Nhắc Nhở")
            }
        },
        dismissButton = {
            if (trip.reminderEnabled) {
                TextButton(onClick = onCancelReminder) {
                    Text("Tắt Nhắc Nhở", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Đóng")
                }
            }
        }
    )
}
