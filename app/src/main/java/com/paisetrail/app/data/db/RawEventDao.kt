package com.paisetrail.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RawEventDao {
    @Insert
    suspend fun insert(event: RawEventEntity): Long

    @Update
    suspend fun update(event: RawEventEntity)

    @Query("SELECT * FROM raw_events ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<RawEventEntity>>

    @Query("SELECT * FROM raw_events WHERE txnId IS NULL ORDER BY postedAt ASC")
    suspend fun getUnlinked(): List<RawEventEntity>

    @Query("SELECT * FROM raw_events WHERE postedAt BETWEEN :fromMillis AND :toMillis ORDER BY postedAt ASC")
    suspend fun getInWindow(fromMillis: Long, toMillis: Long): List<RawEventEntity>
}
