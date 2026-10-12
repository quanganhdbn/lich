package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ParsedTripData(
    val tripDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val startLocation: String = "",
    val destination: String = "",
    val waypoints: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val flightNumber: String = ""
)

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL = "gemini-3.5-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    // Fast, responsive OkHttpClient with 8-second timeouts to prevent app freezing
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun parseScheduleFromPrompt(userInput: String): Result<ParsedTripData> = withContext(Dispatchers.IO) {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Nội dung trống"))
        }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        // Step 1: Always compute local heuristic parsing immediately (ultra fast & deterministic)
        val localParsed = parseLocally(trimmed, todayDate, tomorrowDate)

        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }

        // If no valid key or placeholder key, return local parsed result right away
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.length < 10) {
            return@withContext Result.success(localParsed)
        }

        // Step 2: Try calling Gemini to refine & augment if possible
        try {
            val systemPrompt = """
                Bạn là trợ lý AI chuyên bóc tách thông tin lịch chạy xe đón tiễn từ tin nhắn hoặc văn bản của người dùng.
                Ngày hôm nay là: $todayDate. Ngày mai là: $tomorrowDate.

                Hãy phân tích kỹ tin nhắn của người dùng và trích xuất thành định dạng JSON với các trường sau:
                {
                   "tripDate": "YYYY-MM-DD (nếu nói 'hôm nay' -> $todayDate, 'ngày mai' -> $tomorrowDate, hoặc ngày chỉ định)",
                   "startTime": "HH:mm (giờ đón khách, ví dụ '08:30', '14:00')",
                   "startLocation": "điểm xuất phát / đón khách",
                   "destination": "điểm đến",
                   "customerName": "tên khách hàng hoặc người liên hệ",
                   "customerPhone": "số điện thoại khách hàng",
                   "flightNumber": "mã chuyến bay nếu có (ví dụ 'VN123', 'VJ162', 'QH203')"
                }
                Chỉ trả về JSON duy nhất, không thêm markdown hay giải thích ngoài.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Thông tin lịch chạy xe: $trimmed"))
                        })
                    })
                }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$ENDPOINT?key=$apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful || responseBody.isEmpty()) {
                Log.w(TAG, "Gemini call failed code=${response.code}, using local heuristic result")
                return@withContext Result.success(localParsed)
            }

            val rootObj = JSONObject(responseBody)
            val candidates = rootObj.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            if (text.isEmpty()) {
                return@withContext Result.success(localParsed)
            }

            val parsedJson = JSONObject(text.trim())
            val geminiParsed = ParsedTripData(
                tripDate = parsedJson.optString("tripDate", localParsed.tripDate).ifEmpty { localParsed.tripDate },
                startTime = parsedJson.optString("startTime", localParsed.startTime).ifEmpty { localParsed.startTime },
                endTime = "",
                startLocation = parsedJson.optString("startLocation", localParsed.startLocation).ifEmpty { localParsed.startLocation },
                destination = parsedJson.optString("destination", localParsed.destination).ifEmpty { localParsed.destination },
                waypoints = "",
                customerName = parsedJson.optString("customerName", localParsed.customerName).ifEmpty { localParsed.customerName },
                customerPhone = parsedJson.optString("customerPhone", localParsed.customerPhone).ifEmpty { localParsed.customerPhone },
                flightNumber = parsedJson.optString("flightNumber", localParsed.flightNumber).ifEmpty { localParsed.flightNumber }
            )
            Result.success(geminiParsed)
        } catch (e: Exception) {
            Log.e(TAG, "Error or timeout calling Gemini: ${e.message}, falling back to local heuristic", e)
            Result.success(localParsed)
        }
    }

    /**
     * Highly optimized local Vietnamese NLP extractor for trip booking messages.
     * Can be invoked instantly (0ms) without waiting for network or API quotas.
     */
    fun parseLocally(
        input: String,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
        tomorrow: String = ""
    ): ParsedTripData {
        val calculatedTomorrow = if (tomorrow.isNotEmpty()) tomorrow else {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, 1)
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
        }

        val text = input.trim()
        val lower = text.lowercase()

        // 1. Detect Date
        var date = today
        when {
            lower.contains("ngày mai") || lower.contains("sáng mai") || lower.contains("chiều mai") ||
            lower.contains("tối mai") || lower.contains("trưa mai") || lower.contains("mai") -> {
                date = calculatedTomorrow
            }
            lower.contains("ngày kia") || lower.contains("hôm kia") || lower.contains("kia") -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 2)
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            }
            else -> {
                // Check explicit dd/MM or dd/MM/yyyy
                val dateRegex = Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{4}))?\b""")
                val match = dateRegex.find(text)
                if (match != null) {
                    val d = match.groupValues[1].padStart(2, '0')
                    val m = match.groupValues[2].padStart(2, '0')
                    val y = if (match.groupValues[3].isNotEmpty()) match.groupValues[3] else "2026"
                    date = "$y-$m-$d"
                }
            }
        }

        // 2. Detect Flight Number (e.g. VN123, VJ162, QH203, VU115, etc.)
        val flightRegex = Regex("""(?i)\b(VN|VJ|QH|VU|BL|PA|SQ|TG|KE|QR|EK)\s*(\d{2,4})\b""")
        val flightMatch = flightRegex.find(text)
        val flightNumber = flightMatch?.let {
            "${it.groupValues[1].uppercase()}${it.groupValues[2]}"
        } ?: ""

        // 3. Detect Phone Number
        val phoneRegex = Regex("""\b(0[35789]\d{8})\b""")
        val phoneMatch = phoneRegex.find(text)
        val customerPhone = phoneMatch?.value ?: run {
            val phoneSpaced = Regex("""\b0[35789](\d[\s.-]?){8}\b""").find(text)
            phoneSpaced?.value?.replace(Regex("""[\s.-]"""), "") ?: ""
        }

        // 4. Detect Customer Name
        var customerName = ""
        val nameRegexExplicit = Regex("""(?i)(?:khách\s*(?:hàng|tên|:)?|tên\s*(?:khách)?[:\s]+)\s*([A-ZÀ-Ỹa-zà-ỹ\s]{2,20})""")
        val explicitNameMatch = nameRegexExplicit.find(text)
        if (explicitNameMatch != null) {
            customerName = explicitNameMatch.groupValues[1].trim()
        } else {
            val honorificRegex = Regex("""(?i)\b(anh|chị|bác|chú|cô|sếp)\s+([A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹa-zà-ỹ]+)?)""")
            val honorificMatch = honorificRegex.find(text)
            if (honorificMatch != null) {
                val candidate = honorificMatch.groupValues[2].trim()
                val candidateLower = candidate.lowercase()
                // exclude common non-name words
                if (candidateLower !in listOf("ấy", "này", "kia", "ơi", "tài", "lái", "xe", "khách", "sạn", "bay")) {
                    val prefix = honorificMatch.groupValues[1].replaceFirstChar { it.uppercase() }
                    val namePart = candidate.split(" ").joinToString(" ") { part ->
                        part.replaceFirstChar { it.uppercase() }
                    }
                    customerName = "$prefix $namePart"
                }
            }
        }

        // 5. Detect Time (startTime and optional endTime)
        var startTime = ""
        var endTime = ""
        val timesFound = mutableListOf<String>()

        val timePattern = Regex("""(?i)\b(\d{1,2})(?:h|:)(\d{2})?\b|\b(\d{1,2})\s*(?:giờ|h)\s*(\d{2})?\b""")
        val allMatches = timePattern.findAll(text).toList()

        for (m in allMatches) {
            val hStr = m.groupValues[1].ifEmpty { m.groupValues[3] }
            val mStr = m.groupValues[2].ifEmpty { m.groupValues[4] }.ifEmpty { "00" }
            var hour = hStr.toIntOrNull() ?: continue
            val minute = mStr.padStart(2, '0')

            // Look ahead for afternoon/evening markers
            val matchEnd = m.range.last
            val followingWindow = text.substring(matchEnd, minOf(text.length, matchEnd + 12)).lowercase()
            if ((followingWindow.contains("chiều") || followingWindow.contains("tối")) && hour < 12) {
                hour += 12
            } else if (followingWindow.contains("sáng") && hour == 12) {
                hour = 0
            }

            val formatted = "${hour.toString().padStart(2, '0')}:$minute"
            timesFound.add(formatted)
        }

        if (timesFound.isNotEmpty()) {
            startTime = timesFound[0]
            if (timesFound.size > 1) {
                endTime = timesFound[1]
            }
        } else {
            startTime = "08:30"
        }

        // 6. Detect Route (startLocation & destination)
        var startLoc = ""
        var dest = ""
        var waypoints = ""

        // Check waypoints first
        val waypointRegex = Regex("""(?i)(?:ghé|qua|dừng(?:\s+ở|\s+tại)?)\s+([^,.;\n]+)""")
        val wpMatch = waypointRegex.find(text)
        if (wpMatch != null) {
            waypoints = wpMatch.groupValues[1].trim()
        }

        // Pattern 1: "từ [X] (đi|đến|sang|về|ra) [Y]"
        val fromToRegex = Regex("""(?i)từ\s+(.+?)\s+(?:đi|đến|sang|về|ra)\s+(.+)""")
        val fromToMatch = fromToRegex.find(text)

        // Pattern 2: "(ở|tại|đón tại|đón ở) [X] (đi|đến|sang|về|ra) [Y]"
        val atToRegex = Regex("""(?i)(?:đón\s+)?(?:tại|ở)\s+(.+?)\s+(?:đi|đến|sang|về|ra)\s+(.+)""")
        val atToMatch = atToRegex.find(text)

        // Pattern 3: "(đi|đến|sang|về|ra) [Y] (từ|đón tại|đón ở) [X]"
        val toFromRegex = Regex("""(?i)(?:đi|đến|sang|về|ra)\s+(.+?)\s+(?:từ|đón(?:\s+tại|\s+ở)?)\s+(.+)""")
        val toFromMatch = toFromRegex.find(text)

        when {
            fromToMatch != null -> {
                startLoc = cleanLocationString(fromToMatch.groupValues[1])
                dest = cleanLocationString(fromToMatch.groupValues[2])
            }
            atToMatch != null -> {
                startLoc = cleanLocationString(atToMatch.groupValues[1])
                dest = cleanLocationString(atToMatch.groupValues[2])
            }
            toFromMatch != null -> {
                dest = cleanLocationString(toFromMatch.groupValues[1])
                startLoc = cleanLocationString(toFromMatch.groupValues[2])
            }
            else -> {
                // Heuristics for Airport & Key Destinations
                val hasNoiBai = lower.contains("nội bài") || lower.contains("sân bay nội bài")
                val hasTanSonNhat = lower.contains("tân sơn nhất") || lower.contains("sân bay tân sơn nhất")
                val hasFlight = flightNumber.isNotEmpty()

                val airportName = if (hasTanSonNhat) "Sân bay Tân Sơn Nhất" else "Sân bay Nội Bài"

                if (lower.contains("về") || lower.contains("đón sân bay") || lower.contains("hạ cánh") || lower.contains("đón chuyến")) {
                    // Inbound pickup from airport
                    startLoc = airportName
                    val destMatch = Regex("""(?i)(?:về|đến|sang)\s+([A-ZÀ-Ỹ0-9a-zà-ỹ\s,.-]+)""").find(text)
                    dest = destMatch?.groupValues?.get(1)?.let { cleanLocationString(it) } ?: "Hà Nội"
                } else if (hasNoiBai || hasTanSonNhat || lower.contains("sân bay") || lower.contains("ra sân bay") || lower.contains("đi sân bay")) {
                    // Outbound trip to airport
                    dest = airportName
                    val startMatch = Regex("""(?i)(?:đón\s+(?:tại|ở)?|tại|ở)\s+([A-ZÀ-Ỹ0-9a-zà-ỹ\s,.-]+)""").find(text)
                    startLoc = startMatch?.groupValues?.get(1)?.let { cleanLocationString(it) } ?: "Hà Nội"
                } else {
                    // General "đón tại [X]"
                    val generalStart = Regex("""(?i)(?:đón\s+tại|đón\s+ở|tại|ở)\s+([A-ZÀ-Ỹ0-9a-zà-ỹ\s,.-]+)""").find(text)
                    val generalDest = Regex("""(?i)(?:đi|đến|sang|về)\s+([A-ZÀ-Ỹ0-9a-zà-ỹ\s,.-]+)""").find(text)
                    startLoc = generalStart?.groupValues?.get(1)?.let { cleanLocationString(it) } ?: "Điểm đón"
                    dest = generalDest?.groupValues?.get(1)?.let { cleanLocationString(it) } ?: "Điểm đến"
                }
            }
        }

        // Final sanitation
        if (startLoc.isBlank() || startLoc.equals(dest, ignoreCase = true)) {
            startLoc = if (flightNumber.isNotEmpty() && lower.contains("về")) "Sân bay Nội Bài" else "Điểm đón"
        }
        if (dest.isBlank()) {
            dest = if (flightNumber.isNotEmpty() && !lower.contains("về")) "Sân bay Nội Bài" else "Điểm đến"
        }

        return ParsedTripData(
            tripDate = date,
            startTime = startTime,
            endTime = "",
            startLocation = startLoc,
            destination = dest,
            waypoints = "",
            customerName = customerName,
            customerPhone = customerPhone,
            flightNumber = flightNumber
        )
    }

    private fun cleanLocationString(raw: String): String {
        var str = raw.trim()
        // Strip trailing flight / time / phone / customer phrases
        str = str.replace(Regex("""(?i)\b(bay\s+chuyến|chuyến\s+bay|chuyến|bay|hạ\s+cánh|sđt|số|đt|lúc|ngày|vào\s+lúc|hôm|mai|nhé|nha|ạ|ạ\.)\b.*$"""), "")
        str = str.replace(Regex("""\b(0[35789]\d{8})\b.*$"""), "")
        str = str.replace(Regex("""(?i)\b(VN|VJ|QH|VU|BL)\s*\d{2,4}\b.*$"""), "")
        str = str.replace(Regex("""[,\.;\n]+$"""), "")
        return str.trim()
    }
}
