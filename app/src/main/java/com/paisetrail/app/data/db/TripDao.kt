package com.paisetrail.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Delete
    suspend fun delete(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    /** At most one row ever matches by construction (spec 7.4) — nothing enforces that at the
     * DB level, but every write path in [com.paisetrail.app.trips.TripManager] checks this first. */
    @Query("SELECT * FROM trips WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeActiveTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips ORDER BY startAt DESC")
    fun observeAll(): Flow<List<TripEntity>>
}
