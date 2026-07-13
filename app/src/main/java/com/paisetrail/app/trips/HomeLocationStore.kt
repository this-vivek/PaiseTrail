package com.paisetrail.app.trips

data class HomeLocation(val lat: Double, val lng: Double)

/** User-set home geofence reference point (spec 7.4). Interface so it can be faked in tests
 * without a real Android [android.content.Context] — see [HomeLocationStoreImpl] for the
 * SharedPreferences-backed production implementation. */
interface HomeLocationStore {
    fun get(): HomeLocation?
    fun set(lat: Double, lng: Double)
}
