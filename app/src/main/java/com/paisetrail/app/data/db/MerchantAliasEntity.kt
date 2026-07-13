package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Alternate observed spellings of a merchant's name (spec 6) — not used for resolution yet
 * (Phase 3 fuzzy-matches directly against [MerchantEntity.canonicalName]); reserved for a future
 * merchant-editing UI to record known aliases explicitly. */
@Entity(tableName = "merchant_aliases")
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val merchantId: Long,
)
