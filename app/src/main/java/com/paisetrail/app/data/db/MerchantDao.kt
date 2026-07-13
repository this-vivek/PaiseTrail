package com.paisetrail.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class LatLngRow(val lat: Double, val lng: Double)

@Dao
interface MerchantDao {
    @Insert
    suspend fun insert(merchant: MerchantEntity): Long

    @Update
    suspend fun update(merchant: MerchantEntity)

    @Delete
    suspend fun delete(merchant: MerchantEntity)

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getById(id: Long): MerchantEntity?

    @Query("SELECT * FROM merchants")
    suspend fun getAll(): List<MerchantEntity>

    /** Merchant management screen (spec 4.2/7.6). */
    @Query("SELECT * FROM merchants ORDER BY canonicalName ASC")
    fun observeAll(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE canonicalName = :canonicalName LIMIT 1")
    suspend fun getByName(canonicalName: String): MerchantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkVpa(link: MerchantVpaEntity)

    @Query("SELECT merchantId FROM merchant_vpas WHERE vpa = :vpa LIMIT 1")
    suspend fun getMerchantIdForVpa(vpa: String): Long?

    @Query(
        "SELECT lat, lng FROM transactions " +
            "WHERE merchantId = :merchantId AND paymentContext = 'IN_PERSON' AND lat IS NOT NULL AND lng IS NOT NULL",
    )
    suspend fun getInPersonLocationsForMerchant(merchantId: Long): List<LatLngRow>
}
