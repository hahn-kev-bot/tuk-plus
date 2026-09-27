package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.LatLon
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Distances between points on the map. */
object Distance {
    private const val EARTH_RADIUS_METRES = 6_371_000.0

    /** The web app multiplies the straight distance by this value to estimate the road distance. */
    const val ROAD_FACTOR = 1.3

    /** The straight distance between [a] and [b] on the surface of the earth, in metres. */
    fun haversineMetres(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METRES * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** An estimate of the road distance in metres: round(1.3 × straight distance), like the web app. */
    fun estimatedRoadMetres(a: LatLon, b: LatLon): Int = (ROAD_FACTOR * haversineMetres(a, b)).roundToInt()

    /**
     * Distance text for the shop list: "850 m", "1.7 km", or "12 km" from 10 km.
     * Gives "" when [metres] is null or more than 99 km.
     */
    fun pretty(metres: Int?): String {
        if (metres == null || metres > 99_000) return ""
        val safe = metres.coerceAtLeast(0)
        if (safe < 1000) return "$safe m"
        val tenths = (safe / 100.0).roundToInt()
        return if (tenths < 100) String.format(Locale.US, "%.1f km", tenths / 10.0)
        else "${(safe / 1000.0).roundToInt()} km"
    }
}
