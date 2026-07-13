package com.paisetrail.app.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Haversine distance in meters — shared by [com.paisetrail.app.enrich.MerchantResolver]'s
 * home-location clustering and trip auto-detect's home-geofence check, both of which need "how
 * far apart are these two points" and nothing more precise. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return earthRadiusM * 2 * atan2(sqrt(h), sqrt(1 - h))
}
