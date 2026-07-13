package com.paisetrail.app.trips

import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.data.db.TripEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Manual start/stop (spec 7.4) — auto-detect (spec 7.4's "Looks like you're on a trip?" prompt)
 * goes through [startTrip] too, just triggered from [com.paisetrail.app.enrich.TripAutoDetector]
 * instead of a direct user tap. */
@Singleton
class TripManager @Inject constructor(
    private val tripDao: TripDao,
    private val homeLocationStore: HomeLocationStore,
) {
    suspend fun getActiveTrip(): TripEntity? = tripDao.getActiveTrip()

    /** No-op (returns the existing trip) if one is already active — starting a second trip on
     * top of an active one would silently orphan the first. */
    suspend fun startTrip(name: String, startAt: Long, autoDetected: Boolean = false): TripEntity {
        tripDao.getActiveTrip()?.let { return it }
        val home = homeLocationStore.get()
        val id = tripDao.insert(
            TripEntity(
                name = name,
                startAt = startAt,
                autoDetected = autoDetected,
                homeGeofenceLat = home?.lat,
                homeGeofenceLng = home?.lng,
            ),
        )
        return tripDao.getById(id)!!
    }

    suspend fun endTrip(endAt: Long) {
        val active = tripDao.getActiveTrip() ?: return
        tripDao.update(active.copy(endAt = endAt))
    }
}
