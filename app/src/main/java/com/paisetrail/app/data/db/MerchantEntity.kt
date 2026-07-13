package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Learned merchant record (spec 4.2/6). [defaultCategoryId] is the "merchant's learned
 * defaultCategory" — rule-cascade step 1 in [com.paisetrail.app.enrich.CategoryGuesser]'s spec
 * (4.3), highest priority, set once the user confirms a category for any transaction resolved to
 * this merchant. [homeLat]/[homeLng] are set once >= 3 in-person payments cluster within 150m. */
@Entity(tableName = "merchants")
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canonicalName: String,
    val defaultCategoryId: Long? = null,
    val isOnline: Boolean = false,
    val homeLat: Double? = null,
    val homeLng: Double? = null,
)
