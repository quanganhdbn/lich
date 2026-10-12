package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.local.AppDatabase
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.notification.TripReminderScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TripRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val tripDao = database.tripDao()

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    }

    val allTrips: Flow<List<Trip>> = tripDao.getAllTrips()

    fun getTripsByDate(date: String): Flow<List<Trip>> = tripDao.getTripsByDate(date)

    fun startListeningToCloudTrips(userId: String) {
        val tripsColl = firestore.collection("users").document(userId).collection("trips")
        tripsColl.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, tripsColl.path)
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                val cloudTrips = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.getString("id")?.toLongOrNull() ?: doc.id.toLongOrNull() ?: return@mapNotNull null
                        Trip(
                            id = id,
                            tripDate = doc.getString("tripDate") ?: "",
                            startTime = doc.getString("startTime") ?: "",
                            endTime = doc.getString("endTime") ?: "",
                            startLocation = doc.getString("startLocation") ?: "",
                            destination = doc.getString("destination") ?: "",
                            waypoints = doc.getString("waypoints") ?: "",
                            customerName = doc.getString("customerName") ?: "",
                            customerPhone = doc.getString("customerPhone") ?: "",
                            status = doc.getString("status") ?: TripStatus.SCHEDULED.name,
                            flightNumber = doc.getString("flightNumber") ?: "",
                            flightStatus = doc.getString("flightStatus") ?: "",
                            reminderEnabled = doc.getBoolean("reminderEnabled") ?: false,
                            reminderTimeMillis = doc.getLong("reminderTimeMillis") ?: 0L
                        )
                    } catch (e: Exception) {
                        Log.e("TripRepository", "Failed to parse cloud trip", e)
                        null
                    }
                }
                if (cloudTrips.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        tripDao.insertTrips(cloudTrips)
                    }
                }
            }
        }
    }

    private fun syncTripToFirestore(trip: Trip) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val tripDoc = firestore.collection("users").document(currentUser.uid).collection("trips").document(trip.id.toString())
        val data = hashMapOf<String, Any>(
            "id" to trip.id.toString(),
            "userId" to currentUser.uid,
            "tripDate" to trip.tripDate,
            "startTime" to trip.startTime,
            "endTime" to trip.endTime,
            "startLocation" to trip.startLocation,
            "destination" to trip.destination,
            "waypoints" to trip.waypoints,
            "customerName" to trip.customerName,
            "customerPhone" to trip.customerPhone,
            "status" to trip.status,
            "flightNumber" to trip.flightNumber,
            "flightStatus" to trip.flightStatus,
            "reminderEnabled" to trip.reminderEnabled,
            "reminderTimeMillis" to trip.reminderTimeMillis,
            "createdAt" to FieldValue.serverTimestamp()
        )
        tripDoc.set(data)
            .addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.WRITE, tripDoc.path)
            }
    }

    private fun deleteTripFromFirestore(tripId: Long) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val tripDoc = firestore.collection("users").document(currentUser.uid).collection("trips").document(tripId.toString())
        tripDoc.delete()
            .addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.DELETE, tripDoc.path)
            }
    }

    suspend fun insertTrip(trip: Trip): Long = withContext(Dispatchers.IO) {
        val newId = tripDao.insertTrip(trip)
        val savedTrip = trip.copy(id = newId)

        syncTripToFirestore(savedTrip)

        if (savedTrip.reminderEnabled && savedTrip.reminderTimeMillis > System.currentTimeMillis()) {
            TripReminderScheduler.scheduleTripReminder(
                context = context,
                tripId = newId,
                reminderTimeMillis = savedTrip.reminderTimeMillis,
                title = "Lịch đón lúc ${savedTrip.startTime}",
                content = "${savedTrip.startLocation} ➔ ${savedTrip.destination}"
            )
        }

        newId
    }

    suspend fun updateTrip(trip: Trip) = withContext(Dispatchers.IO) {
        tripDao.updateTrip(trip)
        syncTripToFirestore(trip)

        if (trip.reminderEnabled && trip.reminderTimeMillis > System.currentTimeMillis()) {
            TripReminderScheduler.scheduleTripReminder(
                context = context,
                tripId = trip.id,
                reminderTimeMillis = trip.reminderTimeMillis,
                title = "Lịch đón lúc ${trip.startTime}",
                content = "${trip.startLocation} ➔ ${trip.destination}"
            )
        } else {
            TripReminderScheduler.cancelTripReminder(context, trip.id)
        }
    }

    suspend fun deleteTrip(trip: Trip) = withContext(Dispatchers.IO) {
        TripReminderScheduler.cancelTripReminder(context, trip.id)
        deleteTripFromFirestore(trip.id)
        tripDao.deleteTrip(trip)
    }

    suspend fun updateTripStatus(tripId: Long, newStatus: String) = withContext(Dispatchers.IO) {
        tripDao.updateTripStatus(tripId, newStatus)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val tripDoc = firestore.collection("users").document(currentUser.uid).collection("trips").document(tripId.toString())
            tripDoc.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.UPDATE, tripDoc.path)
            }
        }
    }

    suspend fun seedSampleDataIfNeeded() = withContext(Dispatchers.IO) {
        val tripCount = tripDao.count()
        if (tripCount > 0) return@withContext

        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val today = dateFormat.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFormat.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, 2)
        val tomorrow = dateFormat.format(cal.time)

        val trip1 = Trip(
            id = 1,
            tripDate = today,
            startTime = "08:00",
            startLocation = "Keangnam Landmark 72, Hà Nội",
            destination = "Sân bay Quốc tế Nội Bài (T2)",
            customerName = "Anh Tuấn",
            customerPhone = "0903123456",
            status = TripStatus.SCHEDULED.name,
            flightNumber = "VN123",
            flightStatus = "Vietnam Airlines • Đang bay",
            reminderEnabled = true,
            reminderTimeMillis = System.currentTimeMillis() + 3600000L
        )

        val trip2 = Trip(
            id = 2,
            tripDate = today,
            startTime = "14:00",
            startLocation = "Khu đô thị Starlake Tây Hồ",
            destination = "KCN Yên Phong 1, Bắc Ninh",
            customerName = "Đoàn kỹ sư Samsung",
            customerPhone = "0988776655",
            status = TripStatus.SCHEDULED.name,
            reminderEnabled = true,
            reminderTimeMillis = System.currentTimeMillis() + 7200000L
        )

        val trip3 = Trip(
            id = 3,
            tripDate = today,
            startTime = "18:30",
            startLocation = "Sân bay Nội Bài (Ga đến T1)",
            destination = "Times City, Minh Khai, Hà Nội",
            customerName = "Chị Mai Linh",
            customerPhone = "0977112233",
            status = TripStatus.SCHEDULED.name,
            flightNumber = "VJ162",
            flightStatus = "Vietjet Air • Lên lịch",
            reminderEnabled = true,
            reminderTimeMillis = System.currentTimeMillis() + 10800000L
        )

        val trip4 = Trip(
            id = 4,
            tripDate = yesterday,
            startTime = "07:30",
            startLocation = "Hà Nội",
            destination = "Tam Đảo, Vĩnh Phúc (Khứ hồi)",
            customerName = "Gia đình anh Hải",
            customerPhone = "0933221100",
            status = TripStatus.COMPLETED.name
        )

        val trip5 = Trip(
            id = 5,
            tripDate = tomorrow,
            startTime = "09:00",
            startLocation = "Kho trung chuyển Cầu Bươu, Thanh Trì",
            destination = "KCN Quế Võ, Bắc Ninh",
            customerName = "Kho vận Hải An",
            customerPhone = "0944556677",
            status = TripStatus.SCHEDULED.name,
            reminderEnabled = true,
            reminderTimeMillis = System.currentTimeMillis() + 86400000L
        )

        tripDao.insertTrips(listOf(trip1, trip2, trip3, trip4, trip5))
    }
}
