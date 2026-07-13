package com.paisetrail.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BankSmsPatternDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(patterns: List<BankSmsPatternEntity>)

    /** Add/edit from the bank pattern management screen (spec 7.6) — REPLACE, not IGNORE, since
     * editing an existing bankId (including a seeded one) is the whole point of that screen. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pattern: BankSmsPatternEntity)

    @Delete
    suspend fun delete(pattern: BankSmsPatternEntity)

    @Query("SELECT * FROM bank_sms_patterns ORDER BY bankId ASC")
    fun observeAll(): Flow<List<BankSmsPatternEntity>>

    /** Export (spec 7.6-style JSON export/import, extended to bank patterns) needs disabled rows
     * too, unlike [getEnabled] which the capture path uses. */
    @Query("SELECT * FROM bank_sms_patterns ORDER BY bankId ASC")
    suspend fun getAll(): List<BankSmsPatternEntity>

    @Query("SELECT * FROM bank_sms_patterns WHERE enabled = 1")
    suspend fun getEnabled(): List<BankSmsPatternEntity>

    @Query("SELECT COUNT(*) FROM bank_sms_patterns")
    suspend fun count(): Int
}
