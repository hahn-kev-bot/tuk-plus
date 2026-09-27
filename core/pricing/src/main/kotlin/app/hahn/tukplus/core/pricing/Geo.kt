package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.model.LatLon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Map rules of the web app (docs/pricing.md §7, §10). */
object Geo {
    /**
     * `distanceToUserInMeters` (web module 2c95): haversine with an earth radius of 6371 km,
     * rounded to metres. [StrictMath] gives the same last bits as the JavaScript engine.
     */
    fun straightMetres(a: LatLon, b: LatLon): Double {
        val dLat = deg2rad(b.lat - a.lat)
        val dLon = deg2rad(b.lon - a.lon)
        val n = StrictMath.sin(dLat / 2) * StrictMath.sin(dLat / 2) +
            StrictMath.cos(deg2rad(a.lat)) * StrictMath.cos(deg2rad(b.lat)) * StrictMath.sin(dLon / 2) * StrictMath.sin(dLon / 2)
        val l = 2 * StrictMath.atan2(StrictMath.sqrt(n), StrictMath.sqrt(1 - n))
        val km = 6371 * l
        return Js.round(1e3 * km)
    }

    private fun deg2rad(x: Double): Double = x * (Math.PI / 180)

    /** `tripDistance`: the road distance estimate when there is no route: round(1.25 × straight) + extra. */
    fun tripMetres(pickup: LatLon, dropoff: LatLon, extraDistance: Double = 0.0): Double =
        Js.round(1.25 * straightMetres(pickup, dropoff)) + extraDistance

    /**
     * geojson-utils `pointInPolygon` (web module 3d9a) for `{type: "Polygon", coordinates: rings}`.
     * [rings] is `[[[lon, lat], …], …]`. Bad data gives false.
     */
    fun inPolygon(point: LatLon, rings: JsonElement?): Boolean {
        val polygon = parseRings(rings) ?: return false
        if (polygon.isEmpty() || polygon[0].isEmpty()) return false
        // bounding box of the first ring: [[minLat, minLon], [maxLat, maxLon]]
        val lats = polygon[0].map { it[1] }.sorted()
        val lons = polygon[0].map { it[0] }.sorted()
        if (point.lat < lats.first() || point.lat > lats.last() || point.lon < lons.first() || point.lon > lons.last()) return false
        // ray casting over all rings with [0, 0] between the rings, like the library
        val pts = mutableListOf(doubleArrayOf(0.0, 0.0))
        for (ring in polygon) {
            ring.forEach { pts += it }
            pts += ring[0]
            pts += doubleArrayOf(0.0, 0.0)
        }
        val t = point.lat
        val n = point.lon
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[j]
            if ((a[0] > n) != (b[0] > n) && t < (b[1] - a[1]) * (n - a[0]) / (b[0] - a[0]) + a[1]) inside = !inside
            j = i
        }
        return inside
    }

    private fun parseRings(rings: JsonElement?): List<List<DoubleArray>>? {
        val outer = rings as? JsonArray ?: return null
        return outer.map { ring ->
            (ring as? JsonArray ?: return null).map { p ->
                val arr = p as? JsonArray ?: return null
                if (arr.size < 2) return null
                doubleArrayOf((arr[0] as? JsonPrimitive)?.doubleOrNull ?: return null, (arr[1] as? JsonPrimitive)?.doubleOrNull ?: return null)
            }
        }
    }
}
