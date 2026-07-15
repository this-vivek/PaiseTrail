package com.paisetrail.app.enrich

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/** Text -> coordinates, the reverse of [ReverseGeocodeWorker] — used when a place name/locality
 * exists but no GPS fix was ever taken (a restored backup, or historical data). Android's free
 * Geocoder resolves a place/locality query to city or neighborhood accuracy, not a street
 * address, so callers should tag the result as [com.paisetrail.app.data.db.LocationQuality.APPROXIMATE]
 * rather than GOOD. */
object ForwardGeocoder {
    fun query(placeName: String?, locality: String?): String? =
        listOfNotNull(placeName?.trim()?.ifBlank { null }, locality?.trim()?.ifBlank { null })
            .distinct()
            .joinToString(", ")
            .ifBlank { null }

    @Suppress("DEPRECATION")
    fun resolve(context: Context, placeName: String?, locality: String?): Pair<Double, Double>? {
        val query = query(placeName, locality) ?: return null
        return try {
            Geocoder(context, Locale.getDefault())
                .getFromLocationName(query, 1)
                ?.firstOrNull()
                ?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            null
        }
    }
}
