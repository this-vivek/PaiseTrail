package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** spec 6/7.4. [endAt] null means the trip is currently active — only one trip should be active
 * at a time (enforced by [TripDao]'s query pattern, not a DB constraint). [homeGeofenceLat]/
 * [homeGeofenceLng] snapshot the home location used to auto-detect this trip, so a later change
 * to the user's home setting doesn't retroactively change past trips' detection logic. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startAt: Long,
    val endAt: Long? = null,
    val autoDetected: Boolean = false,
    val homeGeofenceLat: Double? = null,
    val homeGeofenceLng: Double? = null,
)
