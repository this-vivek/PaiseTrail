package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Exact-match lookup table (spec 6): a VPA maps to exactly one merchant. Every fuzzy-matched or
 * newly created merchant resolution learns a new row here so the *next* payment from the same
 * VPA hits the fast exact-match path instead of fuzzy matching again. */
@Entity(tableName = "merchant_vpas")
data class MerchantVpaEntity(
    @PrimaryKey val vpa: String,
    val merchantId: Long,
)
