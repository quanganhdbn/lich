package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class FlightInfo(
    val flightIata: String,
    val airlineName: String,
    val depIata: String,
    val depAirportName: String,
    val depTime: String,
    val arrIata: String,
    val arrAirportName: String,
    val arrTime: String,
    val status: String,
    val delayMinutes: Int = 0,
    val isDemoOrMock: Boolean = false,
    val errorMessage: String? = null
)

object AirlabsService {
    private const val TAG = "AirlabsService"
    private const val BASE_URL = "https://airlabs.co/api/v9"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Airport mapping for Vietnam and popular regional airports
    private val airportNames = mapOf(
        "HAN" to "Sân bay Nội Bài (Hà Nội)",
        "SGN" to "Sân bay Tân Sơn Nhất (TP.HCM)",
        "DAD" to "Sân bay Quốc tế Đà Nẵng",
        "CXR" to "Sân bay Cam Ranh (Nha Trang)",
        "PQC" to "Sân bay Quốc tế Phú Quốc",
        "HPH" to "Sân bay Cát Bi (Hải Phòng)",
        "VDO" to "Sân bay Vân Đồn (Quảng Ninh)",
        "VII" to "Sân bay Vinh (Nghệ An)",
        "HUI" to "Sân bay Phú Bài (Huế)",
        "DLI" to "Sân bay Liên Khương (Đà Lạt)",
        "BKK" to "Sân bay Suvarnabhumi (Bangkok)",
        "SIN" to "Sân bay Changi (Singapore)",
        "ICN" to "Sân bay Incheon (Seoul)",
        "NRT" to "Sân bay Narita (Tokyo)"
    )

    suspend fun getFlightInfo(flightCodeInput: String): FlightInfo = withContext(Dispatchers.IO) {
        val cleanCode = flightCodeInput.trim().uppercase().replace(" ", "")
        val apiKey = try {
            BuildConfig::class.java.getField("AIRLABS_API_KEY").get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }

        // If no real key configured or it's placeholder, provide realistic Vietnamese flight lookup
        if (apiKey.isEmpty() || apiKey == "MY_AIRLABS_API_KEY") {
            return@withContext generateKnownFlightSimulation(cleanCode)
        }

        try {
            // First try real AirLabs /flight endpoint
            val url = "$BASE_URL/flight?flight_iata=$cleanCode&api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "AirLabs response: $responseBody")

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val json = JSONObject(responseBody)
                if (json.has("response")) {
                    val respObj = json.optJSONObject("response")
                    if (respObj != null) {
                        return@withContext parseFlightJson(cleanCode, respObj)
                    }
                }
            }

            // If flight is not airborne, try schedules endpoint
            val schedUrl = "$BASE_URL/schedules?flight_iata=$cleanCode&api_key=$apiKey"
            val schedReq = Request.Builder().url(schedUrl).build()
            val schedResp = client.newCall(schedReq).execute()
            val schedBody = schedResp.body?.string() ?: ""

            if (schedResp.isSuccessful && schedBody.isNotEmpty()) {
                val schedJson = JSONObject(schedBody)
                val respArray = schedJson.optJSONArray("response")
                if (respArray != null && respArray.length() > 0) {
                    val firstSched = respArray.getJSONObject(0)
                    return@withContext parseFlightJson(cleanCode, firstSched)
                }
            }

            // Fallback if not found in live feed
            generateKnownFlightSimulation(cleanCode)
        } catch (e: Exception) {
            Log.e(TAG, "AirLabs API error: ${e.message}", e)
            generateKnownFlightSimulation(cleanCode)
        }
    }

    private fun parseFlightJson(cleanCode: String, obj: JSONObject): FlightInfo {
        val airlineName = obj.optString("airline_name", getAirlineName(cleanCode))
        val depIata = obj.optString("dep_iata", "HAN")
        val arrIata = obj.optString("arr_iata", "SGN")
        val depTime = obj.optString("dep_estimated", obj.optString("dep_time", "08:30"))
        val arrTime = obj.optString("arr_estimated", obj.optString("arr_time", "10:45"))
        val statusRaw = obj.optString("status", "scheduled")
        val delayMin = obj.optInt("delayed", 0)

        val statusText = when (statusRaw.lowercase()) {
            "active" -> "Đang bay"
            "landed" -> "Đã hạ cánh"
            "scheduled" -> "Đã lên lịch"
            "delayed" -> "Bị hoãn (${delayMin}p)"
            "cancelled" -> "Đã hủy"
            else -> "Đúng giờ"
        }

        return FlightInfo(
            flightIata = cleanCode,
            airlineName = airlineName,
            depIata = depIata,
            depAirportName = airportNames[depIata] ?: "Sân bay $depIata",
            depTime = depTime,
            arrIata = arrIata,
            arrAirportName = airportNames[arrIata] ?: "Sân bay $arrIata",
            arrTime = arrTime,
            status = statusText,
            delayMinutes = delayMin,
            isDemoOrMock = false
        )
    }

    private fun getAirlineName(code: String): String {
        return when {
            code.startsWith("VN") -> "Vietnam Airlines"
            code.startsWith("VJ") -> "VietJet Air"
            code.startsWith("QH") -> "Bamboo Airways"
            code.startsWith("VU") -> "Vietravel Airlines"
            code.startsWith("BL") || code.startsWith("PA") -> "Pacific Airlines"
            code.startsWith("SQ") -> "Singapore Airlines"
            code.startsWith("TG") -> "Thai Airways"
            code.startsWith("KE") -> "Korean Air"
            code.startsWith("QR") -> "Qatar Airways"
            code.startsWith("EK") -> "Emirates"
            else -> "Chuyến bay $code"
        }
    }

    private fun generateKnownFlightSimulation(cleanCode: String): FlightInfo {
        val airline = getAirlineName(cleanCode)
        // Realistic default routes for popular flights
        val (dep, arr, depTime, arrTime, status) = when {
            cleanCode.startsWith("VN1") || cleanCode.startsWith("VJ1") ->
                Tuple5("HAN", "SGN", "09:00", "11:15", "Đúng giờ - Dự kiến đến 11:15")
            cleanCode.startsWith("VN2") || cleanCode.startsWith("VJ2") ->
                Tuple5("SGN", "HAN", "14:20", "16:35", "Đang bay - Đúng giờ")
            cleanCode.startsWith("QH") ->
                Tuple5("HAN", "DAD", "10:15", "11:40", "Đã hạ cánh lúc 11:35")
            else ->
                Tuple5("HAN", "SGN", "13:00", "15:10", "Đã lên lịch bay")
        }

        return FlightInfo(
            flightIata = cleanCode,
            airlineName = airline,
            depIata = dep,
            depAirportName = airportNames[dep] ?: "Sân bay $dep",
            depTime = depTime,
            arrIata = arr,
            arrAirportName = airportNames[arr] ?: "Sân bay $arr",
            arrTime = arrTime,
            status = status,
            delayMinutes = 0,
            isDemoOrMock = true
        )
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
