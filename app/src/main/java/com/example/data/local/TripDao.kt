package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY tripDate DESC, startTime ASC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE tripDate = :date ORDER BY startTime ASC")
    fun getTripsByDate(date: String): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: Long): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<Trip>)

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: Long)

    @Query("UPDATE trips SET status = :status WHERE id = :id")
    suspend fun updateTripStatus(id: Long, status: String)

    @Query("UPDATE trips SET status = :status, completedBy = :completedBy, completedAt = :completedAt WHERE id = :id")
    suspend fun updateTripCompleted(id: Long, status: String, completedBy: String, completedAt: Long)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun count(): Int

    @Query("SELECT * FROM trips WHERE reminderEnabled = 1 AND reminderTimeMillis > :nowTime ORDER BY reminderTimeMillis ASC")
    suspend fun getUpcomingReminders(nowTime: Long): List<Trip>
}
