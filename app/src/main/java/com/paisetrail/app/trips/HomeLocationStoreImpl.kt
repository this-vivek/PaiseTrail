package com.paisetrail.app.trips

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** SharedPreferences-backed — a single lat/lng pair doesn't need a database table or migration. */
@Singleton
class HomeLocationStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
) : HomeLocationStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): HomeLocation? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LNG)) return null
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0L))
        val lng = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LNG, 0L))
        return HomeLocation(lat, lng)
    }

    override fun set(lat: Double, lng: Double) {
        prefs.edit()
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_LNG, java.lang.Double.doubleToRawLongBits(lng))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "home_location"
        private const val KEY_LAT = "lat_bits"
        private const val KEY_LNG = "lng_bits"
    }
}
