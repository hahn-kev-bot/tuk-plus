package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.LatLon
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceTest {
    private val centre = LatLon.CHIANG_MAI

    @Test
    fun `haversine is right for known distances`() {
        assertEquals(0.0, Distance.haversineMetres(centre, centre))
        // One degree of latitude is about 111.2 km.
        val oneDegree = Distance.haversineMetres(LatLon(18.0, 99.0), LatLon(19.0, 99.0))
        assertTrue(abs(oneDegree - 111_195) < 50, "$oneDegree")
        // The centre of Chiang Mai to the example shop of api-reference §5.1 is about 5.1 km.
        val shop = Distance.haversineMetres(centre, LatLon(18.751184, 98.97347))
        assertTrue(shop in 4_900.0..5_200.0, "$shop")
    }

    @Test
    fun `road estimate is 1_3 times the straight distance, rounded`() {
        val a = LatLon(18.0, 99.0)
        val b = LatLon(18.01, 99.0)
        assertEquals(Math.round(1.3 * Distance.haversineMetres(a, b)).toInt(), Distance.estimatedRoadMetres(a, b))
        assertEquals(Distance.estimatedRoadMetres(a, b), Distance.estimatedRoadMetres(b, a))
    }

    @Test
    fun `pretty text`() {
        assertEquals("", Distance.pretty(null))
        assertEquals("0 m", Distance.pretty(0))
        assertEquals("850 m", Distance.pretty(850))
        assertEquals("999 m", Distance.pretty(999))
        assertEquals("1.0 km", Distance.pretty(1000))
        assertEquals("1.7 km", Distance.pretty(1_660))
        assertEquals("9.9 km", Distance.pretty(9_940))
        assertEquals("10 km", Distance.pretty(9_960))
        assertEquals("12 km", Distance.pretty(12_400))
        assertEquals("99 km", Distance.pretty(99_000))
        assertEquals("", Distance.pretty(99_001))
    }
}
